package com.igot.cb.pores.exceptions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class RestExceptionHandlingTest {

    private RestExceptionHandling restExceptionHandling;

    @BeforeEach
    void setUp() {
        restExceptionHandling = new RestExceptionHandling();
    }

    @Test
    void testHandleGenericException() {
        Exception ex = new Exception("Something went wrong");

        ResponseEntity<?> response = restExceptionHandling.handleException(ex);

        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        Assertions.assertEquals("ERROR", body.getCode());
        Assertions.assertEquals("Something went wrong", body.getMessage());
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), body.getHttpStatusCode());
    }

    @Test
    void testHandleCustomException_withValidHttpStatus() {
        CustomException ex = new CustomException("Bad request", "BAD_REQ", HttpStatus.BAD_REQUEST);

        ResponseEntity<?> response = restExceptionHandling.handleException(ex);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        Assertions.assertEquals("BAD_REQ", body.getMessage());
        Assertions.assertEquals("Bad request", body.getCode());
        Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), body.getHttpStatusCode());
    }
}
