// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-

#ifndef TRON_SOCKETS_H
#define TRON_SOCKETS_H

#include <iostream>
#include <stdint.h>

/*
Exceptions are not portable. Not to Windows (see pthreadGCE.dll).
class TronException {
public:
    const char *method, *message;
    int number;
    TronException(const char* _method);
    TronException(const char* _method, int errornumber);
    std::ostream& print(std::ostream&);
// (feel free to write your own << operators)
};
*/

/**
 * TCP/IP socket wrapper for writing and reading primitive data
 */
class ClientSocket
{
    int sock;
    const char* host;
    int port;

    const size_t size;
    char* buffer;
    size_t length;
public:
// create a socket on a descriptor (used by ServerSocket)
    ClientSocket(int descriptor, size_t maxWriteBufferSize=256):
        sock(descriptor), host(NULL), port(0),
        size(maxWriteBufferSize), buffer(new char[size]),length(0) {}
// connect to remote TCP/IP socket, use connect() afterwards:
    ClientSocket(const char* _host, int _port, size_t maxWriteBufferSize=256):
        sock(-1), host(_host), port(_port),
        size(maxWriteBufferSize), buffer(new char[size]),length(0) {}
    ~ClientSocket();
    int32_t connect(int retries, int delay_in_secs);
    void closeConnection();
    bool connected() { return (sock>=0); }
    int32_t setLowLatency(); // sets the TCP_NODELAY option
    int32_t readByte(char& c);
    int32_t readInt8(int8_t& b) { return readByte((char&)b); }
    int32_t readUInt8(uint8_t& i) { return readByte((char&)i); }
    int32_t readInt16(int16_t&);
    int32_t readUInt16(uint16_t& i) { return readInt16((int16_t&)i); }
    int32_t readInt32(int32_t&);
    int32_t readUInt32(uint32_t& i) { return readInt32((int32_t&)i); }
    int32_t readInt64(int64_t& i);
    int32_t readUInt64(uint64_t& i) { return readInt64((int64_t&)i); }
    int32_t readNString(char* buffer);

    void writeByte(char b);
    void writeInt8(int8_t v) { writeByte((char)v); }
    void writeUInt8(uint8_t v) { writeByte((char)v); }
    void writeInt16(int16_t v);
    void writeUInt16(uint16_t v) { writeInt16((int16_t)v); }
    void writeInt32(int32_t v);
    void writeUInt32(uint32_t v) { writeInt32((int32_t)v); }
    void writeInt64(const int64_t& v);
    void writeUInt64(const uint64_t& v) { writeInt64((int64_t&)v); }
    void writeNString(const char* buffer);
    void writeNString(const char* buffer, uint8_t len);
    int32_t writeBuffer(const char* buffer, size_t len);
    int32_t flush() {
        if (length) {
            int res;
            res = writeBuffer(buffer, length); length=0;
            return res;
        } else return 0;
    }
};

/**
 * Local TCP/IP socket for accepting connections
 */
class ServerSocket
{
    int sock;
public:
    ServerSocket(int port, int connection_buffer_size=5);
    ~ServerSocket();
    ClientSocket* accept();
};

#endif
