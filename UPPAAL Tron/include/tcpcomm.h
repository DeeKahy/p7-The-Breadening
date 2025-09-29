//                              -*- Mode: C++ -*- 
// Author          : Brian Nielsen    -    bnielsen@iesd.auc.dk
// Created On      : Fri Feb 12 12:26:10 1993
// Last Modified By: Brian Nielsen    -    bnielsen@iesd.auc.dk
// Last Modified On: Wed Apr  7 12:52:44 1993
// Update Count    : 86
// 
// (C) copyright, no warranty
// 

#ifndef __TCP_
#define __TCP_

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>

#ifdef TIME
#include "timer.hh"
#endif
// /////////////////////////////////////////////////////////////////
// High level TCP/IP message passing class  for MTS-Linda 
// but is general enough for different uses
// /////////////////////////////////////////////////////////////////

class TCP
{
protected:
    
#ifdef TIME
  Timer t_read;
  Timer t_write;  
#endif
  
  int	sockfd;                    //socket for new connections
  int confd;                       //the socket on which we listen for 
                                   //new connections 
  int portid;
  struct sockaddr_in	serv_addr; //stores the IP address of the server
  struct hostent * hp;             //used to read entries from /etc/hosts
    

  int maxdesc;                    //how many connections we are abel to hold
  int no_open;                    //how many is actually open
  int no_ready;                   //how many are ready for reading
  int pollpos;                    //which sock did we just service
  
  fd_set read_before;             //a set of sockets to read-multiplex over
  fd_set read_after;              //a set of sockets ready to read
  
  fd_set ex_after;                //a set of sockets with exceptions
  
  int * map;                      //array which holds 1's in elements 
                                  //(socket_id's) are open 
                                  //in this way we keep track of all open 
                                  //connections
                                  //i.e. if map[56]==1 the socket 56 is open
  
public:
  //  int portid;                  //tcp port id used by this instance 
  
  virtual int connect_to(int port_id, const char * hostname);
  // /////////////////////////////////////////////////////////////////
  // Try to connect to server port_id at hostname 
  // Retries a number of times with a little delay
  // returns socket id if success, -1 if failure
  // /////////////////////////////////////////////////////////////////

  int do_accept();
  // /////////////////////////////////////////////////////////////////
  // Accept a connection indication and hence establish connection
  // with client
  //  returns socket id if success, -1 if failure
  //  // /////////////////////////////////////////////////////////////////


  int do_close(int socketid); 
  // /////////////////////////////////////////////////////////////////
  // Closes the connection with id socketid
  // returns -1 if failure
  // /////////////////////////////////////////////////////////////////
  
  int do_read(int socketid, char*buf, int size);
  // /////////////////////////////////////////////////////////////////
  // Read a message from connection socketid into Mbuf
  // returns:
  // -1 if error 
  //      //  else no of bytes read
  // /////////////////////////////////////////////////////////////////


  int do_write(int socketid,char*buf,int size);
  // /////////////////////////////////////////////////////////////////
  // Sends size bytes from Mbuf on connection socketid
  // -1 if error 
  //  else 1
  // /////////////////////////////////////////////////////////////////
  
  int do_poll();
  // /////////////////////////////////////////////////////////////////
  // Tests a set of connections for any readable socketids
  // i.e. MULTIPLEXES - the set of connections to multiplex over
  // can be conetrolled by the use() and unuse() methods.
  // returns event description N:
  // N > 0 ,socket no N ready for READING
  // N == 0, a new connection indication exists - ready for ACCEPT
  // N == -1 ERROR
  // N < -1 socket -N was errornous
  // /////////////////////////////////////////////////////////////////
  
  int use(int socketid);
  // /////////////////////////////////////////////////////////////////
  // Controlls the set of sockets to multiplex over
  // Includes socketid in the set - i.e. socketid is now being tested
  // for ready messages
  // returns -1 if failure
  // /////////////////////////////////////////////////////////////////

  int unuse(int socketid);
  // /////////////////////////////////////////////////////////////////
  // Controlls the set of sockets to multiplex over
  // Excludes socketid from the set - i.e. socketid is not being tested
  // for ready messages
  // /////////////////////////////////////////////////////////////////
  
  int shutdown();
  // /////////////////////////////////////////////////////////////////
  // Closes all open sockets
  // /////////////////////////////////////////////////////////////////
  
  TCP(int port_id);
  // /////////////////////////////////////////////////////////////////
  // This instance becomes server at port_id
  // if port_id is 0 the tcp only works as a client ie. cannot accept 
  // connections!
  // /////////////////////////////////////////////////////////////////
  
  virtual ~TCP();
  int setNoDelay(int sockfd);
  // /////////////////////////////////////////////////////////////////
  // disable Nagle's algorithm on connection sockfd:
  // forces immediate sending of small messages
  // return -1 if failure
  // /////////////////////////////////////////////////////////////////
};


enum connect_type
{
    NUCL_TO_NUCL, //used at boot time to set up system
    EXT_TO_NUCL,  //used by connect_type;
    SHELL_TO_NUCL, //used by new shell
    CLIENT_TO_NUCL //used by new clients
};

//Information send from connector to acceptor upon connection establishment
struct connect_info
{
    connect_type type;    
    int id;
};

// /////////////////////////////////////////////////////////////////
// Simple application protocol built on top of the TCP wrapper: 
//
// It supports three communication modes based on TLV (type,len,value)
// encoding of messages (header) 
// 
// Mode V: only sends/received value from sendValueBuffer/receiveValueBuffer
//
// Mode LV: sends/receives <length,value> message consisting of a
// length field indicating the length of the value, followed by length
// bytes from sendValueBuffer/receiveValueBuffer
//
// Mode TLV sends/receives <type,Length,value> message consisting of a
// type tag, the length of the value, and length bytes from
// sendValueBuffer/receiveValueBuffer
//
// T,L values are sent as ints (32 bit number) in network byte order 
// /////////////////////////////////////////////////////////////////

class SAPTCP: public TCP
{
private:
  int sizelen;
  int sizetype;
  int getInt32(char*buf);
  void putInt32(char*buf, int value);
  void makeBufs(int bufsize);
#ifdef TIME
    Timer t_read;
    Timer t_write;  
#endif
  
  char * realLVSendBuf;
  char * realLVReceiveBuf;
  char * realTLVSendBuf;
  char * realTLVReceiveBuf;


  int bufsize;
public:
  char * receiveValueBuf;
  char * sendValueBuf;

  SAPTCP(int bufsize);
  SAPTCP(int bufsize,int port_id);
  ~SAPTCP();
  

  int connect_to(connect_info * ci, int port_id, const char* hostname);
  // /////////////////////////////////////////////////////////////////
  // Try to connect to server port_id at hostname and send connect info
  // If ci is NULL to connect info is sent
  // Retries a number of times with a little delay
  // returns socket id if success, -1 if failure
  // /////////////////////////////////////////////////////////////////

  int do_accept(connect_info * ci);
  // /////////////////////////////////////////////////////////////////
  // Accept a connection indication and hence establish connection
  // with client. If ci is NULL no connect_info data is received
  //  returns socket id if success, -1 if failure
  //  stores connect info in ci parameter
  // /////////////////////////////////////////////////////////////////
  

  int readV(int socketid, int size);
  int writeV(int socketid,int size);
  
  int writeLV(int socketid,int size);
  int readLV(int socketid);
    
  int writeTLV(int socketid,int type, int size);
  int readTLV(int socketid, int & type);


    
};


#endif
