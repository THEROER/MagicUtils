package dev.ua.theroer.magicutils.annotations;

/**
 * How a command node behaves when several plugins contribute to the same command
 * tree — e.g. two plugins each adding sub-commands under a shared {@code /nox}
 * root. It governs ownership of a node's <em>execute</em> action (the handler that
 * runs when a player stops at that node), not whether sub-commands are added: leaf
 * sub-commands always accumulate.
 *
 * <p>The default is {@link #CONTRIBUTE}, which makes merging safe: a node's execute
 * is claimed only if still free, so contributions accumulate and no plugin silently
 * clobbers another's handler regardless of load order.
 */
public enum MergePolicy {

    /**
     * Claim this node's execute only if no one has claimed it yet (put-if-absent);
     * never overwrite another plugin's execute. The safe default for contributing
     * into a shared root.
     */
    CONTRIBUTE,

    /**
     * Take this node's execute for this contributor, replacing any existing one. If
     * two contributors both OVERRIDE the same node, the last one wins and a warning
     * naming both is logged.
     */
    OVERRIDE,

    /**
     * Only add sub-commands under this node; never claim its execute. Use when a
     * plugin extends a node another plugin owns (e.g. adding {@code worlds create}
     * without owning what {@code /nox worlds} itself runs).
     */
    EXTEND
}
