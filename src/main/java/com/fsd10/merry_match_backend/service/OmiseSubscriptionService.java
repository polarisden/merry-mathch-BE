package com.fsd10.merry_match_backend.service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fsd10.merry_match_backend.dto.subscription.PaymentCardDto;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionCheckoutRequest;
import com.fsd10.merry_match_backend.dto.subscription.SubscriptionCheckoutResponse;
import com.fsd10.merry_match_backend.entity.BillingRecord;
import com.fsd10.merry_match_backend.entity.Plans;
import com.fsd10.merry_match_backend.entity.Subscription;
import com.fsd10.merry_match_backend.entity.User;
import com.fsd10.merry_match_backend.exception.SubscriptionAlreadyActiveException;
import com.fsd10.merry_match_backend.exception.SubscriptionPaymentException;
import com.fsd10.merry_match_backend.repository.PlansRepository;
import com.fsd10.merry_match_backend.repository.SubscriptionRepository;
import com.fsd10.merry_match_backend.repository.UserRepository;

import co.omise.Client;
import co.omise.models.Card;
import co.omise.models.Charge;
import co.omise.models.ChargeStatus;
import co.omise.models.Customer;
import co.omise.models.OmiseException;
import co.omise.models.ScopedList;
import co.omise.models.Token;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OmiseSubscriptionService {

    public static final String META_USER_ID = "user_id";
    public static final String META_PLAN_ID = "plan_id";
    public static final String META_FLOW = "flow";
    public static final String FLOW_SUBSCRIBE = "subscribe";
    public static final String FLOW_PLAN_UPGRADE = "plan_upgrade";
    public static final String FLOW_RENEWAL = "renewal";
    public static final String META_SUBSCRIPTION_ID = "subscription_id";

    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    /** Omise {@link Card#getBrand()} values vary in casing; normalized keys only. */
    private static final Set<String> ALLOWED_CARD_BRANDS_NORMALIZED = Set.of("visa", "mastercard");

    /**
     * Billing period length used to compute current_period_end.
     * Default is P30D (30 calendar days). Override in local/testing to shorten (e.g. PT2M).
     */
    private final Duration billingPeriod;

    private final ObjectProvider<Client> omiseClientProvider;
    private final UserRepository userRepository;
    private final PlansRepository plansRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingAttemptService billingAttemptService;

    public OmiseSubscriptionService(
            @Value("${subscription.billing.period:P30D}") Duration billingPeriod,
            ObjectProvider<Client> omiseClientProvider,
            UserRepository userRepository,
            PlansRepository plansRepository,
            SubscriptionRepository subscriptionRepository,
            BillingAttemptService billingAttemptService
    ) {
        this.billingPeriod = billingPeriod;
        this.omiseClientProvider = omiseClientProvider;
        this.userRepository = userRepository;
        this.plansRepository = plansRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.billingAttemptService = billingAttemptService;
    }

    private Client omiseClient() {
        Client c = omiseClientProvider.getIfAvailable();
        if (c == null) {
            throw new SubscriptionPaymentException("Omise is not configured (set omise.secret-key / OMISE_SECRET_KEY)");
        }
        return c;
    }

    @Transactional
    public SubscriptionCheckoutResponse checkout(UUID userId, SubscriptionCheckoutRequest request)
            throws OmiseException, IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        Plans plan = plansRepository.findById(request.planId())
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));

        assertNoActiveSubscription(userId);

        requireAllowedCardBrandForPaymentToken(request.omiseToken());

        Customer customer = omiseClient().sendRequest(
                new Customer.CreateRequestBuilder()
                        .email(user.getEmail())
                        .description("Merry Match subscription — user " + userId)
                        .card(request.omiseToken())
                        .metadata(META_USER_ID, userId.toString())
                        .build());

        String customerId = customer.getId();
        String cardId = resolveCardIdForNewCustomer(customer);

        Charge charge = omiseClient().sendRequest(
                new Charge.CreateRequestBuilder()
                        .amount(plan.getPriceSatang().longValue())
                        .currency("thb")
                        .customer(customerId)
                        .card(cardId)
                        .capture(true)
                        .description("Subscription: " + plan.getName())
                        .metadata(META_USER_ID, userId.toString())
                        .metadata(META_PLAN_ID, plan.getId().toString())
                        .metadata(META_FLOW, FLOW_SUBSCRIBE)
                        .build());

        log.info(
                "Omise charge created: id={}, status={}, paid={}, amount={} {}",
                charge.getId(),
                charge.getStatus(),
                charge.isPaid(),
                charge.getAmount(),
                charge.getCurrency());

        if (charge.isPaid()) {
            Subscription sub = fulfillPaidCharge(user, plan, customerId, charge);
            return SubscriptionCheckoutResponse.builder()
                    .subscriptionId(sub.getId())
                    .chargeId(charge.getId())
                    .status("paid")
                    .authorizeUri(null)
                    .omiseCustomerId(customerId)
                    .paymentCard(PaymentCardDto.fromSubscription(sub))
                    .build();
        }

        if (charge.getStatus() == ChargeStatus.Pending) {
            billingAttemptService.upsertAttemptIsolated(
                    charge.getId(),
                    BillingRecord.BillingStatus.PENDING,
                    user,
                    null,
                    plan,
                    (int) charge.getAmount(),
                    LocalDateTime.now(BANGKOK),
                    LocalDateTime.now(BANGKOK).plus(billingPeriod),
                    null,
                    null,
                    null);
            return SubscriptionCheckoutResponse.builder()
                    .subscriptionId(null)
                    .chargeId(charge.getId())
                    .status("pending")
                    .authorizeUri(charge.getAuthorizeUri())
                    .omiseCustomerId(customerId)
                    .paymentCard(null)
                    .build();
        }

        String msg = charge.getFailureMessage() != null ? charge.getFailureMessage() : "Charge failed";
        billingAttemptService.upsertAttemptIsolated(
                charge.getId(),
                BillingRecord.BillingStatus.FAILED,
                user,
                null,
                plan,
                (int) charge.getAmount(),
                LocalDateTime.now(BANGKOK),
                LocalDateTime.now(BANGKOK).plus(billingPeriod),
                null,
                charge.getFailureCode(),
                msg);
        throw new SubscriptionPaymentException(msg);
    }

    /**
     * Replace card on Omise customer (no charge): attach token → set default → remove previous cards →
     * persist {@link Subscription#getOmiseCardId()} + display fields for renewals.
     */
    @Transactional
    public PaymentCardDto updatePaymentMethod(UUID userId, String omiseToken) throws OmiseException, IOException {
        Subscription sub = subscriptionRepository.findByUser_Id(userId)
                .orElseThrow(() -> new EntityNotFoundException("No subscription for user"));
        if (sub.getStatus() != Subscription.SubscriptionStatus.ACTIVE) {
            throw new SubscriptionPaymentException("Cannot update card: subscription is not active");
        }
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        if (sub.getCurrentPeriodEnd() == null || !sub.getCurrentPeriodEnd().isAfter(now)) {
            throw new SubscriptionPaymentException("Cannot update card: billing period has ended");
        }
        String customerId = sub.getOmiseCustomerId();
        if (customerId == null || customerId.isBlank()) {
            throw new SubscriptionPaymentException("No saved payment profile; complete a subscription purchase first");
        }

        Customer preCustomer = omiseClient().sendRequest(new Customer.GetRequestBuilder(customerId).build());
        Set<String> cardIdsBeforeAttach = snapshotCustomerCardIds(preCustomer);
        String newCardId = attachPaymentTokenAndResolveNewCardId(customerId, omiseToken, cardIdsBeforeAttach);

        omiseClient().sendRequest(
                new Customer.UpdateRequestBuilder(customerId)
                        .defaultCard(newCardId)
                        .build());

        for (String oldId : cardIdsBeforeAttach) {
            if (oldId == null || oldId.isBlank() || oldId.equals(newCardId)) {
                continue;
            }
            try {
                omiseClient().sendRequest(new Card.DeleteRequestBuilder(customerId, oldId).build());
            } catch (OmiseException e) {
                log.warn(
                        "updatePaymentMethod: failed to delete old card customerId={} cardIdRef={} httpStatus={} message={}",
                        customerId,
                        redactOmiseTokenForLog(oldId),
                        e.getHttpStatusCode(),
                        e.getMessage());
            } catch (IOException e) {
                log.warn(
                        "updatePaymentMethod: IO error deleting old card customerId={} cardIdRef={}",
                        customerId,
                        redactOmiseTokenForLog(oldId),
                        e);
            }
        }

        Card attached = omiseClient().sendRequest(new Card.GetRequestBuilder(customerId, newCardId).build());
        applyCardSnapshotFromCard(sub, attached);
        subscriptionRepository.save(sub);
        return PaymentCardDto.fromSubscription(sub);
    }

    /**
     * Called from webhook after verifying signature. Fetches latest charge state from Omise (source of truth).
     */
    @Transactional
    public void syncChargeFromWebhook(String chargeId) throws OmiseException, IOException {
        Charge charge = omiseClient().sendRequest(new Charge.GetRequestBuilder(chargeId).build());
        if (!charge.isPaid()) {
            log.info("Webhook: charge {} not paid yet (status={}), skipping fulfill", chargeId, charge.getStatus());
            return;
        }
        String flow = readMetadataString(charge.getMetadata(), META_FLOW);
        if (FLOW_SUBSCRIBE.equals(flow)) {
            syncSubscribeChargeFromWebhook(charge);
            return;
        }
        if (FLOW_PLAN_UPGRADE.equals(flow)) {
            Subscription sub = fulfillPlanUpgradePaidCharge(charge);
            log.info("Webhook: plan upgrade applied for subscription {} charge {}", sub.getId(), chargeId);
            return;
        }
        if (FLOW_RENEWAL.equals(flow)) {
            fulfillRenewalPaidCharge(charge);
            log.info("Webhook: renewal applied for charge {}", chargeId);
            return;
        }
        log.warn("Webhook: charge {} unknown metadata {}={}", chargeId, META_FLOW, flow);
    }

    /**
     * ต่ออายุรายเดือน: ใช้ {@link Subscription#getOmiseCardId()} ที่บันทึกไว้ + {@code customerId} สร้าง charge ตาม Omise API
     * (ไม่ดึง default card จาก customer แยก) — DB ขยายรอบเมื่อ charge สำเร็จ (หรือ webhook ถ้า pending).
     */
    @Transactional
    public void chargeRenewalAndFulfill(Subscription sub) throws OmiseException, IOException {
        Plans plan = plansRepository.findById(sub.getPlan().getId())
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));
        UUID userId = sub.getUser().getId();
        long amount = plan.getPriceSatang().longValue();

        String customerId = sub.getOmiseCustomerId();
        if (customerId == null || customerId.isBlank()) {
            throw new SubscriptionPaymentException("Cannot renew: missing Omise customer on subscription");
        }
        String cardId = sub.getOmiseCardId();
        if (cardId == null || cardId.isBlank()) {
            throw new SubscriptionPaymentException(
                    "Cannot renew: no saved card on subscription; add or update your card in membership settings");
        }
        requireAllowedCardBrandForCustomerCard(customerId, cardId);

        Charge charge = omiseClient().sendRequest(
                new Charge.CreateRequestBuilder()
                        .amount(amount)
                        .currency("thb")
                        .customer(customerId)
                        .card(cardId)
                        .capture(true)
                        .description("Subscription renewal: " + plan.getName())
                        .metadata(META_USER_ID, userId.toString())
                        .metadata(META_PLAN_ID, plan.getId().toString())
                        .metadata(META_SUBSCRIPTION_ID, sub.getId().toString())
                        .metadata(META_FLOW, FLOW_RENEWAL)
                        .build());

        log.info(
                "Omise renewal charge: id={}, status={}, paid={}, subscription={}",
                charge.getId(),
                charge.getStatus(),
                charge.isPaid(),
                sub.getId());

        if (charge.isPaid()) {
            fulfillRenewalPaidCharge(charge);
            return;
        }
        if (charge.getStatus() == ChargeStatus.Pending) {
            LocalDateTime now = LocalDateTime.now(BANGKOK);
            LocalDateTime oldEnd = sub.getCurrentPeriodEnd();
            LocalDateTime newStart = (oldEnd == null || oldEnd.isBefore(now)) ? now : oldEnd;
            LocalDateTime newEnd = newStart.plus(billingPeriod);
            billingAttemptService.upsertAttemptIsolated(
                    charge.getId(),
                    BillingRecord.BillingStatus.PENDING,
                    sub.getUser(),
                    sub,
                    plan,
                    (int) charge.getAmount(),
                    newStart,
                    newEnd,
                    null,
                    null,
                    null);
            log.info("Renewal charge pending for subscription {}", sub.getId());
            return;
        }
        String msg = charge.getFailureMessage() != null ? charge.getFailureMessage() : "Charge failed";
        LocalDateTime now = LocalDateTime.now(BANGKOK);
        LocalDateTime oldEnd = sub.getCurrentPeriodEnd();
        LocalDateTime newStart = (oldEnd == null || oldEnd.isBefore(now)) ? now : oldEnd;
        LocalDateTime newEnd = newStart.plus(billingPeriod);
        billingAttemptService.upsertAttemptIsolated(
                charge.getId(),
                BillingRecord.BillingStatus.FAILED,
                sub.getUser(),
                sub,
                plan,
                (int) charge.getAmount(),
                newStart,
                newEnd,
                null,
                charge.getFailureCode(),
                msg);
        log.warn("Renewal charge failed for subscription {}, expiring", sub.getId());
        expireSubscriptionAfterFailedRenewal(sub);
        subscriptionRepository.save(sub);
    }

    private void expireSubscriptionAfterFailedRenewal(Subscription sub) {
        sub.setStatus(Subscription.SubscriptionStatus.EXPIRED);
        sub.setPendingPlan(null);
        sub.setScheduledPlanChangeAt(null);
    }

    private void fulfillRenewalPaidCharge(Charge charge) throws OmiseException, IOException {
        UUID subscriptionId = parseUuid(readMetadataString(charge.getMetadata(), META_SUBSCRIPTION_ID), META_SUBSCRIPTION_ID);
        UUID userId = parseUuid(readMetadataString(charge.getMetadata(), META_USER_ID), "user_id");
        UUID planIdFromMeta = parseUuid(readMetadataString(charge.getMetadata(), META_PLAN_ID), "plan_id");

        Subscription sub = subscriptionRepository.findByIdAndUser_Id(subscriptionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found for renewal"));

        Plans plan = plansRepository.findById(planIdFromMeta)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));
        if (!sub.getPlan().getId().equals(plan.getId())) {
            log.warn("Renewal charge plan_id metadata differs from subscription; using subscription plan");
            plan = sub.getPlan();
        }

        LocalDateTime now = LocalDateTime.now(BANGKOK);
        LocalDateTime oldEnd = sub.getCurrentPeriodEnd();
        /*
         * If the period ended long ago, oldEnd + billingPeriod can still be <= now — the subscription
         * stays "due" forever and cron will charge every tick. Anchor the new window from "now"
         * when overdue so exactly one paid period extends into the future.
         */
        LocalDateTime newStart = (oldEnd == null || oldEnd.isBefore(now)) ? now : oldEnd;
        LocalDateTime newEnd = newStart.plus(billingPeriod);

        sub.setCurrentPeriodStart(newStart);
        sub.setCurrentPeriodEnd(newEnd);
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        sub.setCancelAt(null);
        applyCardSnapshot(sub, charge);

        String customerId = charge.getCustomer();
        if (customerId != null && !customerId.isBlank()) {
            sub.setOmiseCustomerId(customerId);
        }
        subscriptionRepository.save(sub);

        billingAttemptService.upsertAttemptIsolated(
                charge.getId(),
                BillingRecord.BillingStatus.PAID,
                sub.getUser(),
                sub,
                plan,
                (int) charge.getAmount(),
                newStart,
                newEnd,
                toLocalDateTime(charge.getPaidAt()),
                null,
                null);

        if (customerId != null && !customerId.isBlank()) {
            syncOmiseCustomerDefaultCardFromCharge(customerId, charge);
        }
    }

    private void syncSubscribeChargeFromWebhook(Charge charge) throws OmiseException, IOException {
        String chargeId = charge.getId();
        UUID userId = parseUuid(readMetadataString(charge.getMetadata(), META_USER_ID), "user_id");
        UUID planId = parseUuid(readMetadataString(charge.getMetadata(), META_PLAN_ID), "plan_id");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found for metadata user_id"));
        Plans plan = plansRepository.findById(planId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found for metadata plan_id"));

        String customerId = charge.getCustomer();
        Subscription sub = fulfillPaidCharge(user, plan, customerId, charge);
        log.info("Webhook: subscription {} fulfilled for charge {}", sub.getId(), chargeId);
    }

    /**
     * Upgrade แบบจ่ายสัดส่วน — เรียกจาก checkout upgrade หรือ webhook.
     */
    @Transactional
    public SubscriptionCheckoutResponse executePlanUpgradeCharge(
            UUID userId,
            UUID subscriptionId,
            UUID newPlanId,
            String omiseToken,
            long amountSatang
    ) throws OmiseException, IOException {
        Subscription sub = subscriptionRepository.findByIdAndUser_Id(subscriptionId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found"));
        Plans newPlan = plansRepository.findById(newPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found"));

        String customerId = sub.getOmiseCustomerId();
        if (customerId == null || customerId.isBlank()) {
            throw new SubscriptionPaymentException("Subscription has no Omise customer; cannot upgrade");
        }

        Customer preCustomer = omiseClient().sendRequest(new Customer.GetRequestBuilder(customerId).build());
        Set<String> cardIdsBeforeAttach = snapshotCustomerCardIds(preCustomer);
        log.info(
                "executePlanUpgradeCharge: snapshot before attach customerId={} knownCardCount={}",
                customerId,
                cardIdsBeforeAttach.size());

        log.info(
                "executePlanUpgradeCharge: attaching token userId={} subscriptionId={} newPlanId={} "
                        + "customerId={} omiseTokenRef={} planName={}",
                userId,
                subscriptionId,
                newPlanId,
                customerId,
                redactOmiseTokenForLog(omiseToken),
                newPlan.getName());

        String cardId;
        try {
            cardId = attachPaymentTokenAndResolveNewCardId(customerId, omiseToken, cardIdsBeforeAttach);
            log.info("executePlanUpgradeCharge: new card attached customerId={}", customerId);
        } catch (OmiseException e) {
            log.error(
                    "executePlanUpgradeCharge: attach token failed httpStatus={} message={} "
                            + "userId={} subscriptionId={} customerId={} omiseTokenRef={}",
                    e.getHttpStatusCode(),
                    e.getMessage(),
                    userId,
                    subscriptionId,
                    customerId,
                    redactOmiseTokenForLog(omiseToken),
                    e);
            throw e;
        } catch (IOException e) {
            log.error(
                    "executePlanUpgradeCharge: IO error during attach userId={} subscriptionId={} customerId={}",
                    userId,
                    subscriptionId,
                    customerId,
                    e);
            throw e;
        }

        log.info(
                "executePlanUpgradeCharge: sending Charge.Create with card id userId={} subscriptionId={} newPlanId={} "
                        + "amountSatang={} currency=thb customerId={} cardIdRef={} planName={}",
                userId,
                subscriptionId,
                newPlanId,
                amountSatang,
                customerId,
                redactOmiseTokenForLog(cardId),
                newPlan.getName());

        final Charge charge;
        try {
            charge = omiseClient().sendRequest(
                    new Charge.CreateRequestBuilder()
                            .amount(amountSatang)
                            .currency("thb")
                            .customer(customerId)
                            .card(cardId)
                            .capture(true)
                            .description("Plan upgrade (prorated): " + newPlan.getName())
                            .metadata(META_USER_ID, userId.toString())
                            .metadata(META_PLAN_ID, newPlanId.toString())
                            .metadata(META_SUBSCRIPTION_ID, subscriptionId.toString())
                            .metadata(META_FLOW, FLOW_PLAN_UPGRADE)
                            .build());
        } catch (OmiseException e) {
            log.error(
                    "executePlanUpgradeCharge: Omise Charge.Create failed httpStatus={} message={} "
                            + "userId={} subscriptionId={} customerId={} amountSatang={} cardIdRef={}",
                    e.getHttpStatusCode(),
                    e.getMessage(),
                    userId,
                    subscriptionId,
                    customerId,
                    amountSatang,
                    redactOmiseTokenForLog(cardId),
                    e);
            throw e;
        } catch (IOException e) {
            log.error(
                    "executePlanUpgradeCharge: IO error during Charge.Create userId={} subscriptionId={} customerId={}",
                    userId,
                    subscriptionId,
                    customerId,
                    e);
            throw e;
        }

        log.info(
                "Omise plan upgrade charge: id={}, status={}, paid={}, amount={}",
                charge.getId(),
                charge.getStatus(),
                charge.isPaid(),
                charge.getAmount());

        if (charge.isPaid()) {
            Subscription saved = fulfillPlanUpgradePaidCharge(charge);
            return SubscriptionCheckoutResponse.builder()
                    .subscriptionId(saved.getId())
                    .chargeId(charge.getId())
                    .status("paid")
                    .authorizeUri(null)
                    .omiseCustomerId(customerId)
                    .paymentCard(PaymentCardDto.fromSubscription(saved))
                    .build();
        }

        if (charge.getStatus() == ChargeStatus.Pending) {
            billingAttemptService.upsertAttemptIsolated(
                    charge.getId(),
                    BillingRecord.BillingStatus.PENDING,
                    sub.getUser(),
                    sub,
                    newPlan,
                    (int) charge.getAmount(),
                    sub.getCurrentPeriodStart(),
                    sub.getCurrentPeriodEnd(),
                    null,
                    null,
                    null);
            return SubscriptionCheckoutResponse.builder()
                    .subscriptionId(subscriptionId)
                    .chargeId(charge.getId())
                    .status("pending")
                    .authorizeUri(charge.getAuthorizeUri())
                    .omiseCustomerId(customerId)
                    .paymentCard(null)
                    .build();
        }

        String msg = charge.getFailureMessage() != null ? charge.getFailureMessage() : "Charge failed";
        billingAttemptService.upsertAttemptIsolated(
                charge.getId(),
                BillingRecord.BillingStatus.FAILED,
                sub.getUser(),
                sub,
                newPlan,
                (int) charge.getAmount(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                null,
                charge.getFailureCode(),
                msg);
        throw new SubscriptionPaymentException(msg);
    }

    private Subscription fulfillPlanUpgradePaidCharge(Charge charge) throws OmiseException, IOException {
        UUID userId = parseUuid(readMetadataString(charge.getMetadata(), META_USER_ID), "user_id");
        UUID newPlanId = parseUuid(readMetadataString(charge.getMetadata(), META_PLAN_ID), "plan_id");
        UUID subscriptionId = parseUuid(readMetadataString(charge.getMetadata(), META_SUBSCRIPTION_ID), META_SUBSCRIPTION_ID);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found for metadata user_id"));
        Plans newPlan = plansRepository.findById(newPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Plan not found for metadata plan_id"));

        Subscription sub = subscriptionRepository.findByIdAndUser_Id(subscriptionId, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Subscription not found for upgrade"));

        String customerId = charge.getCustomer();

        LocalDateTime periodStart = sub.getCurrentPeriodStart();
        LocalDateTime periodEnd = sub.getCurrentPeriodEnd();

        sub.setPlan(newPlan);
        sub.setPendingPlan(null);
        sub.setScheduledPlanChangeAt(null);
        sub.setOmiseCustomerId(customerId);
        applyCardSnapshot(sub, charge);
        subscriptionRepository.save(sub);

        billingAttemptService.upsertAttemptIsolated(
                charge.getId(),
                BillingRecord.BillingStatus.PAID,
                sub.getUser(),
                sub,
                newPlan,
                (int) charge.getAmount(),
                periodStart,
                periodEnd,
                toLocalDateTime(charge.getPaidAt()),
                null,
                null);

        syncOmiseCustomerDefaultCardFromCharge(customerId, charge);
        return sub;
    }

    /**
     * Aligns Omise customer's {@code default_card} with the card on this charge (matches DB {@code omise_card_id}
     * after {@link #applyCardSnapshot(Subscription, Charge)}).
     */
    private void syncOmiseCustomerDefaultCardFromCharge(String customerId, Charge charge)
            throws OmiseException, IOException {
        if (customerId == null || customerId.isBlank()) {
            return;
        }
        Card card = charge.getCard();
        if (card == null || card.getId() == null || card.getId().isBlank()) {
            return;
        }
        omiseClient().sendRequest(
                new Customer.UpdateRequestBuilder(customerId)
                        .defaultCard(card.getId())
                        .build());
    }

    private static Set<String> snapshotCustomerCardIds(Customer preCustomer) {
        Set<String> cardIdsBeforeAttach = new HashSet<>(extractCustomerCardIds(preCustomer));
        if (preCustomer != null
                && preCustomer.getDefaultCard() != null
                && !preCustomer.getDefaultCard().isBlank()) {
            cardIdsBeforeAttach.add(preCustomer.getDefaultCard());
        }
        return cardIdsBeforeAttach;
    }

    /**
     * Validates token, attaches {@code tokn_} to customer, returns the new {@code card_} id only (never a pre-existing id).
     */
    private String attachPaymentTokenAndResolveNewCardId(
            String customerId,
            String omiseToken,
            Set<String> cardIdsBeforeAttach
    ) throws OmiseException, IOException {
        requireAllowedCardBrandForPaymentToken(omiseToken);
        omiseClient().sendRequest(
                new Customer.UpdateRequestBuilder(customerId)
                        .card(omiseToken.trim())
                        .build());
        Customer postCustomer = omiseClient().sendRequest(new Customer.GetRequestBuilder(customerId).build());
        String newCardId = resolveNewCardIdAfterTokenAttach(cardIdsBeforeAttach, postCustomer);
        if (newCardId == null || newCardId.isBlank()) {
            ScopedList<?> listed = omiseClient().sendRequest(new Card.ListRequestBuilder(customerId).build());
            newCardId = pickCardIdAddedFromScopedList(cardIdsBeforeAttach, listed);
        }
        if (newCardId == null || newCardId.isBlank()) {
            throw new SubscriptionPaymentException(
                    "Could not resolve the new card after token attach; try again or contact support");
        }
        return newCardId;
    }

    /**
     * Resolves the id of the card just added with a token. Omise often leaves {@code default_card} on the
     * previous card briefly — we only accept a {@code card_} id that was <strong>not</strong> in the pre-attach
     * snapshot, so the charge never targets a previously saved card by mistake.
     */
    private static String resolveNewCardIdAfterTokenAttach(Set<String> cardIdsBeforeAttach, Customer postCustomer) {
        if (postCustomer == null) {
            return null;
        }
        String def = postCustomer.getDefaultCard();
        if (def != null && !def.isBlank() && !cardIdsBeforeAttach.contains(def)) {
            return def;
        }
        return pickCustomerCardIdAddedAfterAttach(cardIdsBeforeAttach, postCustomer);
    }

    /**
     * Right after {@link Customer#create} with a token: prefer {@code default_card}, else any {@code card_} on the
     * same response (new customers have only this card).
     */
    private static String resolveCardIdForNewCustomer(Customer customer) {
        if (customer == null) {
            throw new SubscriptionPaymentException("Omise customer missing after create");
        }
        String id = customer.getDefaultCard();
        if (id != null && !id.isBlank()) {
            return id;
        }
        if (customer.getCards() != null && customer.getCards().getData() != null) {
            for (Object o : customer.getCards().getData()) {
                if (o instanceof Card card) {
                    String cid = card.getId();
                    if (cid != null && !cid.isBlank()) {
                        return cid;
                    }
                }
            }
        }
        throw new SubscriptionPaymentException("Omise customer has no card id after token attach");
    }

    private static Set<String> extractCustomerCardIds(Customer c) {
        if (c == null || c.getCards() == null || c.getCards().getData() == null) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (Object o : c.getCards().getData()) {
            if (o instanceof Card card) {
                String id = card.getId();
                if (id != null && !id.isBlank()) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /** Card id present on customer after attach but absent before (the newly tokenized card). */
    private static String pickCustomerCardIdAddedAfterAttach(Set<String> cardIdsBefore, Customer post) {
        if (post == null || post.getCards() == null || post.getCards().getData() == null) {
            return null;
        }
        String chosen = null;
        for (Object o : post.getCards().getData()) {
            if (o instanceof Card card) {
                String cardId = card.getId();
                if (cardId != null && !cardId.isBlank() && !cardIdsBefore.contains(cardId)) {
                    chosen = cardId;
                }
            }
        }
        return chosen;
    }

    private static String pickCardIdAddedFromScopedList(Set<String> cardIdsBefore, ScopedList<?> list) {
        if (list == null || list.getData() == null) {
            return null;
        }
        String chosen = null;
        for (Object o : list.getData()) {
            if (o instanceof Card card) {
                String cardId = card.getId();
                if (cardId != null && !cardId.isBlank() && !cardIdsBefore.contains(cardId)) {
                    chosen = cardId;
                }
            }
        }
        return chosen;
    }

    private Subscription fulfillPaidCharge(User user, Plans plan, String omiseCustomerId, Charge charge) {
        assertNoActiveSubscription(user.getId());

        LocalDateTime periodStart = LocalDateTime.now(BANGKOK);
        LocalDateTime periodEnd = periodStart.plus(billingPeriod);

        Subscription sub = subscriptionRepository.findByUser_Id(user.getId())
                .orElseGet(() -> Subscription.builder()
                        .user(user)
                        .plan(plan)
                        .build());

        sub.setPlan(plan);
        sub.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        sub.setCurrentPeriodStart(periodStart);
        sub.setCurrentPeriodEnd(periodEnd);
        sub.setCancelAt(null);
        sub.setCancelledAt(null);
        sub.setAutoRenew(true);
        sub.setOmiseCustomerId(omiseCustomerId);
        applyCardSnapshot(sub, charge);
        subscriptionRepository.save(sub);

        billingAttemptService.upsertAttemptJoinCaller(
                charge.getId(),
                BillingRecord.BillingStatus.PAID,
                user,
                sub,
                plan,
                (int) charge.getAmount(),
                periodStart,
                periodEnd,
                toLocalDateTime(charge.getPaidAt()),
                null,
                null);
        return sub;
    }

    private static void applyCardSnapshot(Subscription sub, Charge charge) {
        if (charge == null) {
            return;
        }
        applyCardSnapshotFromCard(sub, charge.getCard());
    }

    private static void applyCardSnapshotFromCard(Subscription sub, Card card) {
        if (card == null) {
            return;
        }
        String cid = card.getId();
        sub.setOmiseCardId(cid != null && !cid.isBlank() ? cid : null);
        sub.setCardBrand(card.getBrand());
        sub.setCardLastDigits(card.getLastDigits());
        int month = card.getExpirationMonth();
        int year = card.getExpirationYear();
        sub.setCardExpirationMonth(month > 0 ? month : null);
        sub.setCardExpirationYear(year > 0 ? year : null);
    }

    /**
     * Omise does not accept a "allowed card brands" flag on {@link Charge.CreateRequestBuilder}.
     * Restrict brands by validating the token (vault) or stored card before charging.
     */
    private void requireAllowedCardBrandForPaymentToken(String omiseToken) throws OmiseException, IOException {
        String tid = omiseToken == null ? "" : omiseToken.trim();
        if (!tid.startsWith("tokn_")) {
            throw new SubscriptionPaymentException("Invalid payment token");
        }
        Token token = omiseClient().sendRequest(new Token.GetRequestBuilder(tid).build());
        requireAllowedCardBrand(token.getCard());
    }

    private void requireAllowedCardBrandForCustomerCard(String customerId, String cardId)
            throws OmiseException, IOException {
        if (customerId == null || customerId.isBlank() || cardId == null || cardId.isBlank()) {
            throw new SubscriptionPaymentException(
                    "Missing Omise customer or card on file; update your payment method and try again");
        }
        Card card = omiseClient().sendRequest(new Card.GetRequestBuilder(customerId, cardId).build());
        requireAllowedCardBrand(card);
    }

    private static void requireAllowedCardBrand(Card card) {
        if (card == null || card.getBrand() == null || card.getBrand().isBlank()) {
            throw new SubscriptionPaymentException("Only Visa and Mastercard are accepted.");
        }
        String normalized = normalizeCardBrand(card.getBrand());
        if (!ALLOWED_CARD_BRANDS_NORMALIZED.contains(normalized)) {
            throw new SubscriptionPaymentException("Only Visa and Mastercard are accepted.");
        }
    }

    private static String normalizeCardBrand(String brand) {
        return brand.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    private void assertNoActiveSubscription(UUID userId) {
        subscriptionRepository.findByUser_Id(userId).ifPresent(sub -> {
            if (sub.getStatus() == Subscription.SubscriptionStatus.ACTIVE
                    && sub.getCurrentPeriodEnd() != null
                    && sub.getCurrentPeriodEnd().isAfter(LocalDateTime.now(BANGKOK))) {
                throw new SubscriptionAlreadyActiveException("You already have an active subscription for this period");
            }
        });
    }

    private static String readMetadataString(Map<String, Object> metadata, String key) {
        if (metadata == null) {
            return null;
        }
        Object v = metadata.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static UUID parseUuid(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing metadata " + label);
        }
        return UUID.fromString(raw.trim());
    }

    /**
     * Prefix/suffix + length only — never log a full Omise card token.
     */
    private static String redactOmiseTokenForLog(String token) {
        if (token == null || token.isBlank()) {
            return "absent";
        }
        String t = token.trim();
        int n = t.length();
        if (n <= 14) {
            return "present len=" + n;
        }
        return t.substring(0, 10) + "…" + t.substring(n - 4) + " len=" + n;
    }

    private static LocalDateTime toLocalDateTime(org.joda.time.DateTime dt) {
        if (dt == null) {
            return null;
        }
        return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(dt.getMillis()),
                BANGKOK);
    }
}
