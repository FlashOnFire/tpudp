# TPUDP

This project demonstrate the usage of udp sockets in java through a simple chat application.

## Functionalities

- Create a server connection for each client on a dedicated port
- Send and receive broadcast messages
- Send and receive private messages
- Create, Delete, join and leave chat rooms
- Send and receive messages in chat rooms
- Disable user connection after a certain period of inactivity through a heartbeat mechanism

## Usage

### requirements

If you are using nix, you can simply `nix build` the project and get executables available in the `result/bin` folder.

Otherwise, you will just need java 23 and gradle installed on your machine.

### Server

To start the server, run the following command:

```bash
gradle runChatUDPServer
```

> Note: all clients will try to connect to the server through localhost.

### Client

To start a client, run the following command:

```bash
gradle runChatUDPClient
```

Commands will be available in the client console.
Type `help` to get a list of available commands.
```bash
```

## Architecture

### Class Diagram

```mermaid

```

We are using one Client and one Server class.
When the server receive a connection, it create a Session instance to handle the communication with the client.
We also have a PacketType enum to define the different types of packets that can be sent between the client and the server.
All the packets are 1024 bytes with the first byte being the packet type.

### Sequence Diagram

```mermaid
sequenceDiagram
  participant Client1 as Client1
  participant Client2 as Client2
  participant Client3 as Client3
  participant CentralServer as CentralServer
  participant Socket1 as Socket1
  participant Socket2 as Socket2
  participant Socket3 as Socket3

  rect rgb(250,250,200)
    note left of CentralServer: Clients register with the central server and receive a dedicated socket/port
    Client1 ->> CentralServer: HELLO (IP: localhost, port: 1234, username: "Alice")
    CentralServer -->> Client1: PORT (Socket1, port: 5678)
    Client2 ->> CentralServer: HELLO (IP: localhost, port: 1234, username: "Bob")
    CentralServer -->> Client2: PORT (Socket2, port: 6789)
    Client3 ->> CentralServer: HELLO (IP: localhost, port: 1234, username: "Charlie")
    CentralServer -->> Client3: PORT (Socket3, port: 7890)
  end

  rect rgb(250,250,200)
    note left of CentralServer: Clients send periodic heartbeat signals to keep the connection alive
    Client1 ->> Socket1: HEARTBEAT
    Client2 ->> Socket2: HEARTBEAT
    Client3 ->> Socket3: HEARTBEAT
  end

  rect rgb(250,250,200)
    note left of CentralServer: Client1 sends a broadcast message to all connected clients
    Client1 ->> Socket1: BROADCAST ("Hello, everyone!")
    Socket2 -->> Client2: BROADCAST ("Hello, everyone!")
    Socket3 -->> Client3: BROADCAST ("Hello, everyone!")
  end

  rect rgb(250,250,200)
    note left of CentralServer: Client2 sends a private message to Client3
    Client2 ->> Socket2: PRIVATE (to "Charlie", "Hey, Charlie!")
    Socket3 -->> Client3: PRIVATE ("Hey, Charlie!")
  end
```
