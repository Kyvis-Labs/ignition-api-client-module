package net.dongliu.requests.exception;

/**
 * Thrown when something wrong occurred when load certificate, construct key manager, etc.
 */
public class KeyManagerLoadFailedException extends RequestsException {

    public KeyManagerLoadFailedException(Exception e) {
        super(e);
    }

    public KeyManagerLoadFailedException(String message) {
        super(message);
    }
}
