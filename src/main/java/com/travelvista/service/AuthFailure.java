package com.travelvista.service;

/** A deliberately safe, client-facing authentication error. */
public class AuthFailure extends RuntimeException {
    private final int status;
    public AuthFailure(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
