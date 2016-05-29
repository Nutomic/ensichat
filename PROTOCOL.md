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

All integer fields are in network byte order, and unsigned (unless 
specified otherwise).


Crypto
------

The message body is always are signed with 'SHA256withRSA'. The
signature is written to the 'Encryption Data' part.

The body of content messages is encrypted using a random 256 bit
AES key. The key is then wrapped using RSA with the sender's
private key, and the result written to the 'Encryption Data' part.


Routing
-------
The routing protocol is based on 
[AODVv2](https://datatracker.ietf.org/doc/draft-ietf-manet-aodvv2/),
with various features left out.

TODO: Add Documentation for routing protocol.

There is currently no support for offline messages. If sender and
receiver are not in the same mesh, the message will not arrive.


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
version, type and ID, followed by the length of the message.

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

Hop Limit SHOULD be set to `20` on message creation, and
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


### Route Request (Protocol-Type = 2)

Sent to request a route to a specific Target Address.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                                                               |
    |                      Address (32 bytes)                       |
    |                                                               |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |           OrigSeqNum          |         OriginMetric          |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                          TargMetric                           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Equivalent to the Sequence Number in the message header.

Set OrigMetric = RouterClient.Cost for the Router Client entry
which includes OrigAddr.

If an Invalid route exists in the Local Route Set matching
TargAddr using longest prefix matching and has a valid
sequence number, set TargSeqNum = LocalRoute.SeqNum.
Otherwise, set TargSeqNum = -1. This field is signed.

### Route Reply (Protocol-Type = 3)

Sent as a reply when a Route Request arrives, to inform other nodes
about a route.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |           TargSeqNum          |          TargMetric           |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Set TargMetric = RouterClient.Cost for the Router Client entry
which includes TargAddr.

### Route Error (Protocol-Type = 4)

Notifies other nodes of routes that are no longer available. The target
address MUST be set to the broadcast address.

     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                                                               |
    |                   Packet Source (32 bytes)                    |
    |                                                               |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                                                               |
    |                      Address (32 bytes)                       |
    |                                                               |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                           SeqNum                              |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

Packet Source is the source address of the message triggering this
Route Error. If the route error is not triggered by a message,
this MUST be set to the null address.

Address is the address that is no longer reachable.

SeqNum is the sequence number of the route that is no longer available
(if known). Otherwise, set TargSeqNum = -1. This field is signed.

Content Messages
----------------

These messages are initiated by user action. They are encrypted, and
contain the Content-Type and Message ID fields. 

These messages always have a Protocol-Type of 255.


### Text (Content-Type = 6)

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

### UserInfo (Content-Type = 7)

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
