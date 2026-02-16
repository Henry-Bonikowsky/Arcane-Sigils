package com.miracle.arcanesigils.interception;

/**
 * Result returned by an interceptor after processing an event.
 */
public class InterceptionResult {
    
    /** Pass through without modification */
    public static final InterceptionResult PASS = new InterceptionResult(false);
    
    /** Event was modified (reduced, cancelled, etc.) */
    public static final InterceptionResult MODIFIED = new InterceptionResult(true);
    
    private final boolean modified;
    
    public InterceptionResult(boolean modified) {
        this.modified = modified;
    }
    
    public boolean wasModified() {
        return modified;
    }
}
