package com.hsbc.fraud.model;

/**
 * Status of a fraud alert in the workflow.
 */
public enum AlertStatus {
    NEW,           // Newly created alert
    INVESTIGATING, // Under investigation
    CONFIRMED,     // Confirmed as fraud
    FALSE_POSITIVE,// Confirmed as false positive
    RESOLVED,      // Alert has been resolved
    ESCALATED      // Escalated to higher authority
}

