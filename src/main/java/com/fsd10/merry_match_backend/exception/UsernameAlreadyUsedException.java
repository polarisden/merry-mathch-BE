package com.fsd10.merry_match_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UsernameAlreadyUsedException extends RuntimeException {
  public UsernameAlreadyUsedException(String username) {
    super("Username already used: " + username);
  }
}

