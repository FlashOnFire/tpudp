package fr.polytech;

public enum PacketType {
    HELLO(1),
    BROADCAST(2),
    PRIVATE(3),

    // Server-Only
    PORT(5),
    NAME_ALREADY_TAKEN(6),
    NEW_USER(6),
    USER_LIST(7),

    // Client-Only
    HEARTBEAT(8);

    private final int id;

    PacketType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static PacketType fromId(int id) {
        for (PacketType type : PacketType.values()) {
            if (type.getId() == id) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown packet type: " + id);
    }
}