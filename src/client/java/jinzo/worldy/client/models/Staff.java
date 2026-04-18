package jinzo.worldy.client.models;

import org.jetbrains.annotations.NotNull;

public record Staff(String name, String uuid) {
    public Staff(@NotNull String name, @NotNull String uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    @Override
    public @NotNull String name() {
        return name;
    }

    @Override
    public @NotNull String uuid() {
        return uuid;
    }

    @Override
    public @NotNull String toString() {
        return "Staff{" +
                "name='" + name + '\'' +
                "uuid='" + uuid + '\'' +
                '}';
    }
}
