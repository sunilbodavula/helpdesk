package com.example.helpdesk.exception;

public class ForbiddenException extends RuntimeException{

    public ForbiddenException(String message){
        super(message);
    }
}
