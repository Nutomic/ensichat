Introduction and Definitions
----------------------------

This protocol is used by two or more devices forming a mesh net.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
"SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this
document are to be interpreted as described in RFC 2119.

A node is a single device implementing this protocol. Each node has
exactl one node address.

A node address consists of 32 bytes and is the SHA-256 hash of the
node's public key.

The broadcast address is
`0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF`
(i.e. all bits set).

The null address is
`0x0000000000000000000000000000000000000000000000000000000000000000`
(i.e. no bits set).

Nodes MUST NOT use a public key with the broadcast address or null
address as hash (they must generate a new key pair). Also, other
nodes MUST NOT connect to a node with either address.


Messages
--------

### Header

Every message starts with one 32 bit word indicating the message
version, type and ID, followed by the length of the message. The
header is in network byte order, i.e. big endian.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |  Ver  |          Type         |   Hop Limit   |   Hop Count   |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                            Length                             |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                             Time                              |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                                                               |
    |                       Origin Address                          |
    |                                                               |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                                                               |
    |                       Target Address                          |
    |                                                               |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |        Sequence Number        |     Metric    |   Reserved    |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Ver specifies the protocol version number. This is currently 0. A
message with unknown version number MUST be ignored. The connection
where such a packet came from MAY be closed.

Hop Limit SHOULD be set to `MAX_HOP_COUNT` on message creation, and
MUST NOT be changed by a forwarding node.

Hop Count specifies the number of nodes a message may pass. When
creating a package, it is initialized to 0. Whenever a node forwards
a package, it MUST increment the hop limit by one. If the hop limit
BEFORE/AFTER? incrementing equals Hop Limit, the package MUST be
ignored.

Length is the message size in bytes, including the header.

Time is the unix timestamp of message creation, in seconds, as a
signed integer.

Origin Address is the address of the node that initially created the
message.

Target Address is the address of the node that should receive the
message.

Sequence number is the sequence number of either the source or target
node for this message, depending on type.


ConnectionInfo (Type = 0)
---------

After successfully connecting to a node via Bluetooth, public keys
must be exchanged. Each node MUST send this as the first message over
the connection. Hop Limit MUST be 1 for this message type (i.e. it
must never be forwarded). Origin Address and Target Address MUST be
set to all zeros, and MUST be ignored by the receiving node.

A receiving node SHOULD store the key in permanent storage if it
hasn't already stored it earlier. This key is to be used for message
encryption when communicating with the sending node.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                          Key Length                           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                   Key (variable length)                       \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Key length is the size of the key in bytes.

Key is the public key of the sending node.

After this message has been received, communication with normal messages
may start.


### Data (Data Transfer, Type = 255)

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                             Length                            |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                       Data (variable length)                  \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Length is the number of bytes in data.

Data is any binary data that should be transported.

This message type is deprecated.