package com.shop.billing;

/** An error we want to show to the user, with the HTTP status to send. */
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException bad(String message) { return new ApiException(400, message); }

    public static ApiException notFound(String message) { return new ApiException(404, message); }

    public static ApiException conflict(String message) { return new ApiException(409, message); }
}
