package com.ultimate.inventory.DTO;

/** A person creating their own account. Companies come later: they create one or get invited. */
public record SignUpDTO(String firstName, String otherName, String lastName, String email,
                        String username, String telephonePrimary, String password) {}
