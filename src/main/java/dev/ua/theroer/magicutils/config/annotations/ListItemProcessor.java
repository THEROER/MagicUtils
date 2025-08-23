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
         * Item is valid as-is.
         */
        public static <T> ProcessResult<T> ok(T value) {
            return new ProcessResult<>(value, false, false);
        }
        
        /**
         * Item was modified.
         */
        public static <T> ProcessResult<T> modified(T value) {
            return new ProcessResult<>(value, true, false);
        }
        
        /**
         * Replace with default value.
         */
        public static <T> ProcessResult<T> replaceWithDefault() {
            return new ProcessResult<>(null, false, true);
        }
        
        public T getValue() { return value; }
        public boolean isModified() { return modified; }
        public boolean shouldUseDefault() { return useDefault; }
    }
}