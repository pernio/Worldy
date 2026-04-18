package jinzo.worldy.client.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import jinzo.worldy.client.utils.CommandHelper;
import jinzo.worldy.client.utils.RuleHelper;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;


import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class RuleCommand {
    public static LiteralArgumentBuilder<FabricClientCommandSource> register() {
        return literal("rule")
                .executes(ctx -> {
                    RuleHelper.loadRulesAsync();

                    CommandHelper.sendMessage(
                            Text.translatable("command.worldy.rule.title")
                                    .formatted(Formatting.GOLD, Formatting.BOLD)
                    );

                    var rules = RuleHelper.allRules();
                    if (rules.isEmpty()) {
                        CommandHelper.sendMessage("command.worldy.data.loading");
                        return 1;
                    }

                    rules.forEach(rule ->
                            CommandHelper.sendMessage(
                                    Text.literal(rule.id + " - " + rule.title)
                                            .formatted(Formatting.GRAY)
                            )
                    );

                    return 1;
                })
                .then(argument("number", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            String typed = context.getInput();
                            int lastSpace = typed.lastIndexOf(' ');
                            if (lastSpace != -1) typed = typed.substring(lastSpace + 1);

                            var rules = RuleHelper.allRules();
                            for (var rule : rules) {
                                if (rule.id.startsWith(typed)) {
                                    builder.suggest(rule.id);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "number");
                            RuleHelper.loadRulesAsync();

                            RuleHelper.ruleById(id).ifPresentOrElse(rule -> {
                                CommandHelper.sendMessage(
                                        Text.literal(rule.id + " - " + rule.title)
                                                .formatted(Formatting.GOLD, Formatting.BOLD)
                                );
                                CommandHelper.sendMessage(
                                        Text.literal(rule.description)
                                                .formatted(Formatting.GRAY)
                                );
                            }, () -> {
                                CommandHelper.sendError("command.worldy.rule.not_found");
                            });

                            return 1;
                        })
                );
    }
}
