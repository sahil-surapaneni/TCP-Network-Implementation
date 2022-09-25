# TCP-Network-Implementation
Compsci 640 Final Project Implementation of TCP (Transmission Control Protocol)

## Introduction
The Transmission Control Protocol (TCP) standard is defined in the Request For Comment (RFC) standards document number 793 by the Internet Engineering Task Force (IETF). The original specification written in 1981 was based on earlier research and experimentation in the original ARPANET. The design of TCP was heavily influenced by what has come to be known as the “end-to-end argument.”

As it applies to the Internet, the end-to-end argument says that by putting excessive intelligence in physical and link layers to handle error control, encryption or flow control you unnecessarily complicate the system. This is because these functions will usually need to be implemented at the endpoints anyways, so duplication of this functionality in the intermediate points can be a waste. The result of an end-to-end network then, is to provide minimal functionality on a hop-by-hop basis and maximal control between end- to-end communicating systems. The end-to-end argument helped determine the design of various components of TCP’s reliability, flow control, and congestion control algorithms. The following are a few important characteristics of TCP.

TCP interfaces between the application layer above and the network layer below. When an application sends data to TCP, it does so in 8-bit byte streams. It is then up to the sending TCP to segment or delineate the byte stream in order to transmit data in manageable pieces to the receiver. It is this lack of record boundaries which give it the name byte stream delivery service.

Before two communicating TCP endpoints can exchange data, they must first agree upon the willingness to communicate. Analogous to a telephone call, a connection must first be made before two parties exchange information.

A number of mechanisms help provide the reliability TCP guarantees. Each of these is described briefly below.
**Checksums**: All TCP segments carry a checksum, which is used by the receiver to detect errors with either the TCP header or data.
**Duplicate data detection**: It is possible for packets to be duplicated in packet switched network; therefore TCP keeps track of bytes received in order to discard duplicate copies of data that has already been received.
**Retransmissions**: In order to guarantee delivery of data, TCP must implement retransmission schemes for data that may be lost or damaged. The use of positive acknowledgements by the receiver to the sender confirms successful reception of data. The lack of positive acknowledgements, coupled with a timeout period (see timers below) calls for a retransmission.
**Sequence numbers**: In packet switched networks, it is possible for packets to be delivered out of order. It is TCP’s job to properly sequence segments it receives so it can deliver the byte stream data to an application in order.
**Timers**: TCP maintains various static and dynamic timers on data sent. The sending TCP waits for the receiver to reply with an acknowledgement within a bounded length of time. If the timer expires before receiving an acknowledgement, the sender can retransmit the segment

## Handshake
To start, Host A initiates the connection by sending a TCP segment with the SYN control bit set and an initial sequence number (ISN) we represent as the variable x in the sequence number field.

At some moment later in time, Host B receives this SYN segment, processes it and responds with a TCP segment of its own. The response from Host B contains the SYN control bit set and its own ISN represented as variable y. Host B also sets the ACK control bit to indicate the next expected byte from Host A should contain data starting with sequence number x+1.

When Host A receives Host B’s ISN and ACK, it finishes the connection establishment phase by sending a final acknowledgement segment to Host B. In this case, Host A sets the ACK control bit and indicates the next expected byte from Host B by placing acknowledgement number y+1 in the acknowledgement field.

In addition to the information shown in the diagram above, an exchange of source and destination ports to use for this connection are also included in each senders’ segments.

## Data Transfer
Once ISNs have been exchanged, communicating applications can transmit data between each other. Most of the discussion surrounding data transfer requires us to look at flow control and congestion control techniques which we discuss later in this document. A few key ideas will be briefly made here, while leaving the technical details aside.

A simple TCP implementation will place segments into the network for a receiver as long as there is data to send and as long as the sender does not exceed the window advertised by the receiver. As the receiver accepts and processes TCP segments, it sends back positive cumulative acknowledgements, indicating the next expected byte in sequence. If data is duplicated or lost, a “hole” may exist in the byte stream. A receiver will continue to acknowledge the most current contiguous byte it has accepted.

If data queued by the sender reaches a point where data sent will exceed the receiver’s advertised window size, the sender must halt transmission and wait for further acknowledgements and an advertised window size that is greater than zero before resuming.

Timers are used to avoid deadlock and unresponsive connections. Delayed transmissions are used to make more efficient use of network bandwidth by sending larger “chunks” of data at once rather than in smaller individual pieces.

## Implementation
**Reliability** (with proper re-transmissions in the event of packet losses / corruption)
**Data Integrity** (with checksums)
**Connection Management** (SYN and FIN)
**Optimizations** (fast retransmit on 3 or more duplicate ACKs)
