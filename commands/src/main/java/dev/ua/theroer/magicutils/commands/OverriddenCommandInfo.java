package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.MergePolicy;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A {@link CommandInfo} view that applies a {@link MagicCommand}'s runtime name and
 * alias overrides on top of its declared (or builder-supplied) metadata. The alias
 * collections are read live; the name is captured when the view is created, which is
 * indistinguishable once the command is frozen by registration.
 */
final class OverriddenCommandInfo implements CommandInfo {
    private final CommandInfo base;
    private final String overrideName;
    private final List<String> addedAliases;
    private final Set<String> removedAliases;

    OverriddenCommandInfo(CommandInfo base, String overrideName,
                          List<String> addedAliases, Set<String> removedAliases) {
        this.base = base;
        this.overrideName = overrideName;
        this.addedAliases = addedAliases;
        this.removedAliases = removedAliases;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return CommandInfo.class;
    }

    @Override
    public String name() {
        return overrideName != null ? overrideName : base.name();
    }

    @Override
    public String description() {
        return base.description();
    }

    @Override
    public String[] aliases() {
        List<String> aliases = new ArrayList<>(Arrays.asList(base.aliases()));
        aliases.addAll(addedAliases);
        aliases.removeIf(a -> removedAliases.contains(a.toLowerCase(Locale.ROOT)));
        return aliases.toArray(new String[0]);
    }

    @Override
    public String permission() {
        return base.permission();
    }

    @Override
    public MagicPermissionDefault permissionDefault() {
        return base.permissionDefault();
    }

    @Override
    public CommandThreading threading() {
        return base.threading();
    }

    @Override
    public MergePolicy merge() {
        return base.merge();
    }
}
