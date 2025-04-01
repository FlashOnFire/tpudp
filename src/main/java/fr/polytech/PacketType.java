package fr.polytech;

public enum PacketType {
    HELLO,
    BROADCAST,
    PRIVATE,
    ROOM_MESSAGE,

    // Server-Only
    PORT,
    NAME_ALREADY_TAKEN,
    NEW_USER,
    USER_LIST,
    ROOM_LIST,
    ROOM_SWITCH,

    // Client-Only,
    HEARTBEAT,
    CREATE_ROOM,
    DELETE_ROOM,
    JOIN_ROOM;

    private int id;

    static {
        int i = 0;
        for (PacketType type : PacketType.values()) {
            type.id = i++;
        }
    }

    public int getId() {
        return id;
    }

    public static PacketType fromId(int id) {
        if (id < 0 || id >= PacketType.values().length) {
            throw new IllegalArgumentException("Unknown packet type: " + id);
        }

        return PacketType.values()[id];
    }
}