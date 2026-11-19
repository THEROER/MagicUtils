package dev.ua.theroer.magicutils.commands;

import dev.ua.theroer.magicutils.annotations.CommandInfo;
import dev.ua.theroer.magicutils.annotations.DefaultValue;
import dev.ua.theroer.magicutils.annotations.Option;
import dev.ua.theroer.magicutils.annotations.OptionalArgument;
import dev.ua.theroer.magicutils.annotations.ParamName;
import dev.ua.theroer.magicutils.annotations.Permission;
import dev.ua.theroer.magicutils.annotations.SubCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandMethodIntrospectorTest {

    @Test
    void argumentsDistinguishDefaultedFromNullableParameters() throws Exception {
        Method method = Sample.class.getDeclaredMethod("execute", String.class, String.class, String.class);

        List<CommandArgument> args = CommandMethodIntrospector.arguments(method);

        assertEquals(3, args.size());
        CommandArgument required = args.get(0);
        assertEquals("target", required.getName());
        assertFalse(required.isOptional());

        CommandArgument defaulted = args.get(1);
        assertTrue(defaulted.isOptional());
        assertFalse(defaulted.isNullable(), "a @DefaultValue alone does not make null acceptable");
        assertEquals("@sender", defaulted.getDefaultValue());

        CommandArgument nullable = args.get(2);
        assertTrue(nullable.isOptional());
        assertTrue(nullable.isNullable());
    }

    @Test
    void flagOptionsDefaultToFalse() throws Exception {
        Method method = Sample.class.getDeclaredMethod("flagged", boolean.class);

        CommandArgument flag = CommandMethodIntrospector.arguments(method).get(0);

        assertTrue(flag.isFlag());
        assertTrue(flag.isOptional());
        assertEquals("false", flag.getDefaultValue());
    }

    @Test
    void permissionWhenExpressionIsParsedIntoACondition() throws Exception {
        Method method = Sample.class.getDeclaredMethod("guarded", String.class);

        CommandArgument argument = CommandMethodIntrospector.arguments(method).get(0);

        assertEquals(PermissionConditionType.SELF, argument.getPermissionCondition());
        assertArrayEquals(new String[] {"target"}, argument.getPermissionConditionArgs());
        assertTrue(argument.isPermissionDeclared());
    }

    @Test
    void subCommandsAreCollectedOncePerSignatureAcrossTheHierarchy() {
        List<MagicCommand.SubCommandInfo> subs = CommandMethodIntrospector.subCommands(Child.class);

        assertEquals(List.of("reload", "status"),
                subs.stream().map(sub -> sub.annotation.name()).sorted().toList());
    }

    @Test
    void findExecuteMethodIgnoresSubCommandMethodsNamedExecute() {
        assertNotNull(CommandMethodIntrospector.findExecuteMethod(Sample.class));
        assertNull(CommandMethodIntrospector.findExecuteMethod(Child.class));
    }

    @Test
    void usageListsTopLevelRoutes() {
        assertEquals("/parent <status>", CommandMethodIntrospector.usage(Parent.class));
        assertNull(CommandMethodIntrospector.usage(Object.class));
    }

    @CommandInfo(name = "sample")
    static class Sample extends MagicCommand {
        @SuppressWarnings("unused")
        public CommandResult execute(@ParamName("target") String target,
                                     @ParamName("who") @DefaultValue("@sender") String who,
                                     @ParamName("note") @OptionalArgument String note) {
            return CommandResult.success();
        }

        @SuppressWarnings("unused")
        public CommandResult flagged(@Option(longNames = {"force"}, flag = true) boolean force) {
            return CommandResult.success();
        }

        @SuppressWarnings("unused")
        public CommandResult guarded(@ParamName("target") @Permission(value = "x", when = "self(target)") String target) {
            return CommandResult.success();
        }
    }

    @CommandInfo(name = "parent")
    static class Parent extends MagicCommand {
        @SubCommand(name = "status")
        public CommandResult status() {
            return CommandResult.success();
        }
    }

    @CommandInfo(name = "child")
    static class Child extends Parent {
        @Override
        @SubCommand(name = "status")
        public CommandResult status() {
            return CommandResult.success();
        }

        @SubCommand(name = "reload")
        public CommandResult execute() {
            return CommandResult.success();
        }
    }
}
