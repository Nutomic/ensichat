Introduction and Definitions
----------------------------

This protocol is used by two or more devices forming a mesh net.

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT",
"SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY", and "OPTIONAL" in this
document are to be interpreted as described in RFC 2119.

A _node_ is a single device implementing this protocol. Each node has
a 4096 bit RSA key pair. This key pair is used for message signing
and encryption. Every node has exactly one node address.

A _node address_ consists of 32 bytes and is the SHA-256 hash of the
node's public key.

The _broadcast address_ is
`0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF`
(i.e. all bits set).

The _null address_ is
`0x0000000000000000000000000000000000000000000000000000000000000000`
(i.e. no bits set).

Nodes MUST NOT have a public key with the broadcast address or null
address as hash. Additionally, nodes MUST NOT connect to a node with
either address.


Crypto
------

The message body is always are signed with 'SHA256withRSA'. The
signature is written to the 'Encryption Data' part.

The body of content messages is encrypted using a random 256 bit
AES key. The key is then wrapped using RSA with the sender's
private key, and the result written to the 'Encryption Data' part.


Routing
-------
A simple flood routing protocol is currently used. Every node forwards
all messages, unless a message with the same Origin and Sequence Number
has already been received.

Nodes MUST store pairs of (Origin, Sequence Number) for all received
messages. After receiving a new message, entries with the same Origin
and Sequence Number between _received_ + 1 and _received_ + 32767 MUST
be removed (with a wrap around at the maximum value). The entries MUST
NOT be cleared while the program is running. They MAY be cleared when
the program is exited.

There is currently no support for offline messages. If sender and
receiver are not in the same mesh, the message will not arrive.

Nodes are free implement different routing algorithms.


Messages
--------

All messages are signed using RSASSA-PKCS1-v1_5. All Content Messages
except are encrypted using AES/CBC/PKCS5Padding, after which the 
AES key is wrapped with the recipient's public RSA key.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                   Header (74 or 84 bytes)                     \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \               Encryption Data (variable length)               \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                    Body (variable length)                     \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+


### Header

Every message starts with one 74 byte header indicating the message
version, type and ID, followed by the length of the message. The
header is in network byte order, i.e. big endian. The header may have
6 bytes of additional data.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |    Version    | Protocol-Type |   Hop Limit   |   Hop Count   |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                            Length                             |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                                                               |
    |                   Origin Address (32 bytes)                   |
    |                                                               |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                                                               |
    |                   Target Address (32 bytes)                   |
    |                                                               |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |         Sequence Number       |         Content-Type          |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                           Message ID                          |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                             Time                              |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Version specifies the protocol version number. This is currently 0. A
message with unknown version number MUST be ignored. The connection
where such a packet came from MAY be closed.

Protocol-Type is one of those specified in section Protocol Messages,
or 255 for Content Messages.

Hop Limit SHOULD be set to `MAX_HOP_COUNT` on message creation, and
MUST NOT be changed by a forwarding node.

Hop Count specifies the number of nodes a message may pass. When
creating a package, it is initialized to 0. Whenever a node forwards
a package, it MUST increment the hop limit by one. If the hop count
after incrementing equals or is greater than Hop Limit, the package
MUST NOT be forwarded.

Length is the message size in bytes, including the header.

Time is the unix timestamp of message creation, in seconds, as a
signed integer.

Origin Address is the address of the node that initially created the
message.

Target Address is the address of the node that should receive the
message.

Sequence number is set by the sender, and MUST increment by 1 for
each new message sent (after 2^16-1 comes 0 again). It SHOULD
be persistent during restarts. It is used by intermediate nodes
to avoid forwarding the same message multiple times.

Content-Type is one of those in section Content-Messages.

Message ID is unique for each message by the same sender. A device
MUST NOT ever send two messages with the same Message ID.

Time is the unix timestamp of message sending.

Only Content Messages have the Message ID and Time fields.

### Encryption Data

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |        Signature Length       |          Key Length           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                   Signature (variable length)                 \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                      Key (variable length)                    \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Encryption key is the symmetric key that was used to encrypt the message
body.

Signature is the cryptographic signature over the (unencrypted) message
header and message body.


Protocol Messages
-----------------

These messages are sent by the protocol, without any user interaction.
They are not encrypted, and do not contain the Content-Type and 
Message ID fields.


### ConnectionInfo  (Protocol-Type = 1)

After successfully connecting to a node via Bluetooth, public keys
are exchanged. Each node MUST send this as the first message over
the connection. Hop Limit MUST be 1 for this message type (i.e. it
must never be forwarded). Origin Address, Target Address and Sequence
Number MUST be set to all zeros, and MUST be ignored by the receiving 
node.

A receiving node SHOULD store the key in permanent storage if it
hasn't already stored it earlier.  However, a node MAY decide to
delete these stored keys in a least-recently-used order to adhere
to storage limitations. If a key has been deleted, messages to
that node can only be sent once a new ConnectionInfo message
for it has been received.


This key is to be used for message encryption when communicating
with the sending node.

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


Content Messages
----------------

These messages are initiated by user action. They are encrypted, and
contain the Content-Type and Message ID fields. 

These messages always have a Protocol-Type of 255.


### RequestAddContact (Content-Type = 1)

Sent when a user wants to add another node as a contact. After this,
a ResultAddContact message should be returned.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                           Reserved                            |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+


### ResultAddContact (Content-Type = 2)

Sent as response to a RequestAddContact message.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |A|                          Reserved                           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Accepted bit (A) is true if the user accepts the new contact, false
otherwise. Nodes should only add another node as a contact if both
users agreed.


### Text (Content-Type = 3)

A simple chat message.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                          Text Length                          |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                   Text (variable length)                      \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Text the string to be transferred, encoded as UTF-8.

### UserInfo (Content-Type = 4)

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                          Name Length                          |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                   Name (variable length)                      \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                         Status Length                         |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    /                                                               /
    \                  Status (variable length)                     \
    /                                                               /
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Contains the sender's name and status, which should be used for
display to users.
