package com.fsd10.merry_match_backend.dto.subscription;

import com.fsd10.merry_match_backend.entity.Subscription;

/**
 * Non-sensitive card display fields only (Omise last_digits + brand + expiry). No PAN/CVV.
 */
public record PaymentCardDto(
        String brand,
        String lastDigits,
        Integer expirationMonth,
        Integer expirationYear
) {
    public static PaymentCardDto fromSubscription(Subscription sub) {
        if (sub == null) {
            return null;
        }
        if (sub.getCardBrand() == null && sub.getCardLastDigits() == null) {
            return null;
        }
        return new PaymentCardDto(
                sub.getCardBrand(),
                sub.getCardLastDigits(),
                sub.getCardExpirationMonth(),
                sub.getCardExpirationYear());
    }
}
