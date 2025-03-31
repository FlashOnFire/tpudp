# TPUDP

This project demonstrates the usage of UDP sockets in Java through a simple chat application.

## Functionalities

- Create a server connection for each client on a dedicated port
- Send and receive broadcast messages
- Send and receive private messages
- Create, delete, join, and leave chat rooms
- Send and receive messages in chat rooms
- Disable user connections after a certain period of inactivity through a heartbeat mechanism

## Usage

### Requirements

If you are using Nix, you can simply `nix build` the project and get executables available in the `result/bin` folder.

Otherwise, you will just need Java 23 and Gradle installed on your machine.

### Server

To start the server, run the following command:

```bash
gradle runChatUDPServer
```

> Note: All clients will try to connect to the server through localhost.

### Client

To start a client, run the following command:

```bash
gradle runChatUDPClient
```

You are now able to send messages in your current room ("General") by typing the message and pressing Enter.
Commands are also available in the client console.
Type `/help` to get a list of available commands.
```bash
/help
===== COMMANDS =====
/bc <message>      - Broadcast message to all users
/msg <user> <msg>  - Send private message to a specific user
/room <name>       - Join an existing room
/createroom <name> - Create a new room
/deleteroom <name> - Delete an existing room
/users             - Display all online users
/rooms             - Display all available rooms
/help              - Show this help message
/quit              - Exit the chat application
===================
```

> Note: All commands begin with a `/` and are case-sensitive.

## Architecture

### Class Diagram

```mermaid
classDiagram
direction BT
class ChatUDPClient {
  + ChatUDPClient()
  + main(String[]) void
}
class ChatUDPServer {
  + ChatUDPServer()
  - ArrayList~String~ rooms
  - String baseRoom
  - ConcurrentHashMap~String, Session~ sessions
  - broadcast(String) void
  - deleteRoom(String) boolean
  - forgeRoomSwitchPacket(String) ByteBuffer
  - switchRoom(String, String) void
  - forgeRoomListPacket() ByteBuffer
  - sendRoomMessage(String, String, String) void
  + main(String[]) void
  - forgeUserListPacket() ByteBuffer
  - createRoom(String) boolean
  - sendPrivateMessage(String, String, String) boolean
}
class PacketType {
<<enumeration>>
  - PacketType(int)
  +  USER_LIST
  +  ROOM_SWITCH
  +  PRIVATE
  +  CREATE_ROOM
  +  ROOM_MESSAGE
  +  HELLO
  +  NAME_ALREADY_TAKEN
  +  DELETE_ROOM
  +  NEW_USER
  +  ROOM_LIST
  - int id
  +  BROADCAST
  +  JOIN_ROOM
  +  HEARTBEAT
  +  PORT
  + getId() int
  + fromId(int) PacketType
  + values() PacketType[]
  + valueOf(String) PacketType
}
class Session {
  + Session(String, String, Runnable, Consumer~String~, BiPredicate~String, String~, Supplier~ByteBuffer~, Supplier~ByteBuffer~, Predicate~String~, Predicate~String~, BiConsumer~String, String~, Consumer~String~)
  - DatagramSocket socket
  - String currentRoom
  - String name
  - int destPort
  - InetAddress address
  - boolean firstHeartbeat
  + getCurrentRoom() String
  + setCurrentRoom(String) void
  + send(ByteBuffer) void
  + getPort() int
  + getName() String
}

ChatUDPServer "1" *--> "sessions *" Session
ChatUDPServer  ..>  Session : «create»
```

We are using one Client and one Server class.
When the server receives a connection, it creates a Session instance to handle the communication with the client.
We also have a PacketType enum to define the different types of packets that can be sent between the client and the server.
All the packets are 1024 bytes, with the first byte being the packet type.

### Sequence Diagram

```mermaid
sequenceDiagram
  participant Client1 as Client1
  participant Client2 as Client2
  participant Client3 as Client3
  participant Client4 as Client4
  participant CentralServer as CentralServer
  participant Socket1 as Socket1
  participant Socket2 as Socket2
  participant Socket3 as Socket3

  rect rgb(230,240,255)
     note left of CentralServer: Clients register with the central server and receive a dedicated socket/port
    Client1 ->> CentralServer: HELLO (IP: localhost, port: 1234, username: "Alice")
    CentralServer -->> Client1: PORT (Socket1, port: 5678)

    Client2 ->> CentralServer: HELLO (IP: localhost, port: 1234, username: "Bob")
    CentralServer -->> Client2: PORT (Socket2, port: 6789)

    Client3 ->> CentralServer: HELLO (IP: localhost, port: 1234, username: "Charlie")
    CentralServer -->> Client3: PORT (Socket3, port: 7890)

    Client4 ->> CentralServer: HELLO (IP: localhost, port: 1234, username: "Alice")
    CentralServer -->> Client4: NAME_ALREADY_TAKEN
  end

  rect rgb(255,235,205)
    note left of CentralServer: Clients send periodic heartbeat signals to keep the connection alive
    Client1 ->> Socket1: HEARTBEAT
    opt First HeartBeat
        Socket1 -->> Client1: USER_LIST
        Socket1 -->> Client1: ROOM_LIST
        Socket1 -->> Client1: ROOM_SWITCH ("General")
    end

    Client2 ->> Socket2: HEARTBEAT
        opt First HeartBeat
    Socket2 -->> Client2: USER_LIST
    Socket2 -->> Client2: ROOM_LIST
    Socket2 -->> Client2: ROOM_SWITCH ("General")
    end
    Client3 ->> Socket3: HEARTBEAT
        opt First HeartBeat
    Socket3 -->> Client3: USER_LIST
    Socket3 -->> Client3: ROOM_LIST
    Socket3 -->> Client3: ROOM_SWITCH ("General")
    end
  end

  rect rgb(250,250,200)
    note left of CentralServer: Client1 sends a broadcast message to all connected clients
    Client1 ->> Socket1: BROADCAST ("Hello, everyone!")
    Socket2 -->> Client2: BROADCAST ("Hello, everyone!")
    Socket3 -->> Client3: BROADCAST ("Hello, everyone!")
  end

  rect rgb(255,215,235)
      note left of CentralServer: Client2 sends a private message to Client3
    Client2 ->> Socket2: PRIVATE (to "Charlie", "Hey, Charlie!")
    Socket3 -->> Client3: PRIVATE ("Hey, Charlie!")
  end

    rect rgb(220,255,220)
    note left of CentralServer: Client1 creates a new room
    Client1 ->> CentralServer: CREATE_ROOM ("Gaming Room")
    CentralServer -->> Client1: ROOM_LIST (["Gaming Room"])
    CentralServer -->> Client2: ROOM_LIST (["Gaming Room"])
    CentralServer -->> Client3: ROOM_LIST (["Gaming Room"])
  end

  rect rgb(255,220,220)
    note left of CentralServer: Client2 joins the "Gaming Room"
    Client2 ->> CentralServer: JOIN_ROOM ("Gaming Room")
    CentralServer -->> Client2: ROOM_SWITCH ("Gaming Room")
  end

    rect rgb(240,230,255)
    note left of CentralServer: Client3 sends a message in the "Gaming Room"
    Client3 ->> CentralServer: ROOM_MESSAGE ("Anyone up for a game?")
    CentralServer -->> Client1: ROOM_MESSAGE ("Anyone up for a game?")
    CentralServer -->> Client2: ROOM_MESSAGE ("Anyone up for a game?")
  end

  rect rgb(200,255,250)
        note left of CentralServer: Client1 deletes the "Gaming Room"
    Client1 ->> CentralServer: DELETE_ROOM ("Gaming Room")
    CentralServer -->> Client2: ROOM_LIST ([])
    CentralServer -->> Client3: ROOM_LIST ([])
  end
```
