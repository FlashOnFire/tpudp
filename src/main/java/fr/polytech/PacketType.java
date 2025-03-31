package fr.polytech;

public enum PacketType {
    HELLO(1),
    BROADCAST(2),
    PRIVATE(3),
    ROOM_MESSAGE(4),

    // Server-Only
    PORT(5),
    NAME_ALREADY_TAKEN(6),
    NEW_USER(7),
    USER_LIST(8),
    ROOM_LIST(9),
    ROOM_SWITCH(10),

    // Client-Only,
    HEARTBEAT(11),
    CREATE_ROOM(12),
    DELETE_ROOM(13),
    JOIN_ROOM(14);

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