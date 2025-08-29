package dev.ua.theroer.magicutils.config.annotations;

/**
 * Interface for processing list items during configuration loading.
 * @param <T> the type of list items
 */
public interface ListItemProcessor<T> {
    /**
     * Process a single list item.
     * @param item the item to process
     * @param index the item's index in the list
     * @return the processing result
     */
    ProcessResult<T> process(T item, int index);
    
    /**
     * Result of processing a list item.
     * @param <T> the type of the item
     */
    class ProcessResult<T> {
        private final T value;
        private final boolean modified;
        private final boolean useDefault;
        
        private ProcessResult(T value, boolean modified, boolean useDefault) {
            this.value = value;
            this.modified = modified;
            this.useDefault = useDefault;
        }
        
        /**
         * Creates a result indicating the item is valid as-is without modification.
         * @param value the original item value
         * @param <T> the type of the item
         * @return a ProcessResult indicating no changes are needed
         */
        public static <T> ProcessResult<T> ok(T value) {
            return new ProcessResult<>(value, false, false);
        }
        
        /**
         * Creates a result indicating the item was modified to a new value.
         * @param value the modified item value
         * @param <T> the type of the item
         * @return a ProcessResult with the modified value
         */
        public static <T> ProcessResult<T> modified(T value) {
            return new ProcessResult<>(value, true, false);
        }
        
        /**
         * Creates a result indicating the item should be replaced with the default value.
         * @param <T> the type of the item
         * @return a ProcessResult indicating the default value should be used
         */
        public static <T> ProcessResult<T> replaceWithDefault() {
            return new ProcessResult<>(null, false, true);
        }
        
        /**
         * Gets the processed value.
         * @return the processed value, or null if shouldUseDefault() is true
         */
        public T getValue() { return value; }
        
        /**
         * Checks if the item was modified during processing.
         * @return true if the item was modified
         */
        public boolean isModified() { return modified; }
        
        /**
         * Checks if the default value should be used instead of the processed value.
         * @return true if the default value should be used
         */
        public boolean shouldUseDefault() { return useDefault; }
    }
}