package com.paddleup.rooms.dto;

/**
 * A franchise's name, colour and monogram. To create one all three are needed; in an edit a null
 * field is left as it is.
 */
public record FranchiseRequest(String name, String colour, String monogram) {}
