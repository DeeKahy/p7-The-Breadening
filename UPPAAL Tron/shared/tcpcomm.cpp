//                              -*- Mode: C++ -*- 
// Author          : Brian Nielsen    -    bnielsen@iesd.auc.dk
// Created On      : Fri Feb 12 12:29:10 1993
// Last Modified By: Tom Soerensen    -    tom@iesd.auc.dk
// Last Modified On: Fri Jun 11 11:14:03 1993
// Update Count    : 228
// 
// (C) copyright, no warranty
// 

#include "tcpcomm.h"

#include <stdio.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/uio.h>
#include <stdlib.h>
#include <unistd.h>
#include <netdb.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <memory.h>
#include <netinet/tcp.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <inttypes.h>

#include <arpa/inet.h>

#include <strings.h>
#include <assert.h>


#define RETRY 10      //the number of connection estableshment retries
#define DELAY 3      //delay between each retry


extern "C" int  shutdown(int, int);


int TCP::connect_to(int port_id, const char* hostname)
{
  //fprintf(stderr,"CONNECT TO %s\n",hostname);

  //read hosts IP address from host DB

  in_addr_t addr;
  if ((int)(addr = inet_addr(hostname)) == -1) {
    hp = gethostbyname(hostname);// no an IP address, maybe hostname?
    if(hp==NULL){
      fprintf(stderr,"network: can't locate %s\n", hostname);
      exit (2);
    }
  } else {// it is an IP address
    hp = gethostbyaddr((char *)&addr, sizeof (addr), AF_INET);
    if(hp == NULL){
      int e = h_errno;
      fprintf(stderr, "network: error #%d, %s\n", e, hstrerror(e));
      exit(2);
    }
  }

  int retry=0;
  while(retry<RETRY) {

    //write server address to address struct
    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family      = AF_INET;
    bcopy((char*) hp->h_addr,(char*)& serv_addr.sin_addr,hp->h_length);
    serv_addr.sin_port = htons(port_id);
    if ((sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0)
      {
	perror("client: can't open socket\n");
	return -1;
      }

    if (connect(sockfd, (struct sockaddr *) &serv_addr,
		sizeof(serv_addr)) < 0)
      {
	perror("client: can't connect\n");
	fprintf(stderr,"(*   RETRY %d   *)\n",retry++);

	//it seems necessary to close the socket and re-open
	//it for the retry-mechanism to be successfull

	if(close(sockfd)<0) {
	  perror("client: can't close retry socket\n");
	  return -1;		
	}
	    
	if(retry==RETRY) return -1;	    
	sleep(DELAY);	       //Delay between retries
      }
    else
      retry=RETRY;	
  }
    

  map[sockfd]=1;                    //this socket is now open

  no_open++;
  
  //    fprintf(stderr,"did connect\n");
    
  return sockfd;
    
}

int TCP::setNoDelay(int sockfd) {
  int flag = 1;
  int result = setsockopt(sockfd,            /* socket affected */
			  IPPROTO_TCP,     /* set option at TCP level */
			  TCP_NODELAY,     /* name of option */
			  (char *) &flag,  /* the cast is historical
					      cruft */
			  sizeof(int));    /* length of option value */
  if (result < 0) {
    perror("client: can't set socket NO_DELAY \n");
  }
  return result;	
    
}



int TCP::do_write(int socketid,char * buf, int size)
{
#ifdef TCP_DEBUG
    fprintf(stderr,"try write %d bytes on %d\n",size,socketid);
#endif /*TCP_DEBUG*/

    //check no write to closed socket
    if(map[socketid]!=1)
	return -1;
    
    int written=0; //total no of bytes written
    int wrote=0;   //no bytes written in a single call
    
    int leftover=size;
    written=0;

#ifdef TIME
    t_write.start();
#endif
    
    while(written<size)    {
      if((wrote=write(socketid,buf,leftover))<=0)	{
	if(wrote<0) {
	  if((errno==EINTR) || (errno==EAGAIN)) continue;	    
	  perror("client: write error\n");
	  return -1;
	}
	//else 0 was written: re-write
      }
      buf+=wrote;
      written+=wrote;
      leftover-=wrote;
    }
#ifdef TIME
    t_write.stop();
    //    fprintf(stderr,"wrote %d bytes in %ld usecs\n",written,t_write.new_time);
#endif
#ifdef TCP_DEBUG
    fprintf(stderr, "WROTE %d BYTES of ORDERED %d\n",written,size);
#endif /*TCP_DEBUG*/
    return 1;    
}
int TCP::do_read(int socketid, char * buf,int size)
{
#ifdef TCP_DEBUG
    fprintf(stderr,"try read\n");
#endif /*TCP_DEBUG*/
    if(map[socketid]!=1)
    {	
	fprintf(stderr,"NOT OPEN DESCRIPTOR %d\n",socketid);	
	return -1;    
    }
    
    int did_read=0;   //how many bytes read in a single read
    int total_read=0; //total no of read bytes
    int leftover=size;
    
    //receive the first 4 bytes of a message - they indicate howlong the
    //rest of the message is.


#ifdef TIME
    t_read.start();
#endif
    
    


#ifdef TIME
    t_read.start();
#endif
    //fprintf(stderr,"MSG LEN=%d\n",leftover);
    while(total_read<size)
    {	
	did_read=read(socketid,buf,leftover);

	if(did_read<=0) {
	  if(did_read<0) {
	    if((errno==EINTR) || (errno==EAGAIN)) continue;	    
	    perror("client: write error\n");
	    return -1;
	  }
	  else
	    if(did_read==0) {
	      fprintf(stderr,"WARNING: read 0\n");
	      return -1;
	    }
	}
	
	buf+=did_read;
	total_read+=did_read;
	leftover-=did_read;
    }    

#ifdef TIME
    t_read.stop();
//   fprintf(stderr,"Read %d bytes in %ld usecs\n",total_read,t_read.new_time);

#endif
#ifdef TCP_DEBUG
    fprintf(stderr,  "READ %d BYTES of Ordered %d \n",total_read,size );
#endif /*TCP_DEBUG*/
    
    

    return total_read;    
}

// /////////////////////////////////////////////////////////////////
// Include socketid in multiplex set
// /////////////////////////////////////////////////////////////////
int TCP::use(int socketid)
{   
    if(map[socketid]!=1)
    {
	fprintf(stderr,"ERROR: socket not open %d\n",socketid);
	
	return -1;
    }
#ifdef TCP_DEBUG
    fprintf(stderr,"USING %d\n",socketid);
#endif /*TCP_DEBUG*/
    
    no_ready=0;                      //force re-select in do_poll()    
    FD_SET(socketid,&read_before);   //include in multiplex set
    return 0;    
}

// /////////////////////////////////////////////////////////////////
// Exclude socketid in multiplex set
// /////////////////////////////////////////////////////////////////
int TCP::unuse(int socketid)
{
    if(map[socketid]!=1)
	return -1;    
    no_ready=0;                     //force re-select in do_poll()
    
    FD_CLR(socketid,&read_before);  //exclude socketid
    return 1;    
}

// /////////////////////////////////////////////////////////////////
// DO_ACCEPT be server for new connection
// /////////////////////////////////////////////////////////////////
int TCP::do_accept()
{
    
    //fprintf(stderr,"try accept\n");
    //someone is trying to contact me!
    //and i am only a client - ?!?!
    if(portid==0) 
	return -1;
    
    //accept the connection
    socklen_t addrlen=sizeof(sockaddr_in);  
    int newfd= accept(confd,(struct sockaddr *) 0,&addrlen);
        
    if (newfd < 0){
      perror("server: accept error\n");
    }
    //fprintf(stderr,"accepted\n");


    map[newfd]=1; //newfd is now in use

    //read connect_info message sent by client
    no_open++;
    return newfd;
    
}

// /////////////////////////////////////////////////////////////////
// DO_CLOSE closes connection
// /////////////////////////////////////////////////////////////////
int TCP::do_close(int socketid)
{
#ifdef TCP_DEBUG
    fprintf(stderr,"close %d\n",socketid);
#endif /*TCP_DEBUG*/
    if(map[socketid]!=1) 
	return -1;

    //exclude from multiplex set
    FD_CLR(socketid,&read_before);
    no_ready=0; //force re-select in do_poll()
    
    if(close(socketid)<0)
    {
      perror("server: close error\n");
	return -1;
    }
    map[socketid]=0; //socketid is now free
    return 1;    
}

// /////////////////////////////////////////////////////////////////
// DESTRUCTOR
// /////////////////////////////////////////////////////////////////
TCP::~TCP()
{
  shutdown();
  delete [] map;
} 

// /////////////////////////////////////////////////////////////////
// CONSTRUCTOR - listen for connections at port_id
// /////////////////////////////////////////////////////////////////
TCP::TCP(int port_id)
{
#ifdef TCP_DEBUG
    fprintf(stderr,"CONSTRUCTING TCP\n");
#endif /*TCP_DEBUG*/
    portid=port_id;
    confd=0;	    
    no_open=0;
    pollpos=0;
    no_ready=0;


    FD_ZERO (&read_before); //init multiplex sets
    FD_ZERO (&read_after);
    FD_ZERO (&ex_after);
    
    //change the soft limit of open descriptors to the max allowed number
    //ca. (256) - if you need more you need to be superuser, but the
    //kernel will probably run out of buffer space way before!

    struct rlimit rlp;
    if(getrlimit(RLIMIT_NOFILE,&rlp)<0)
    {
      perror("server: can't read resource limit\n");
    }
    else
    {
	rlp.rlim_cur=rlp.rlim_max; //allow max no of open descriptors	
	maxdesc=rlp.rlim_max-5;    //use all for tcp, but save a few
                                   // fd's for logfile & stdio
	
	if(setrlimit(RLIMIT_NOFILE,&rlp)<0)	  {
	  perror("server: can't set resource limit\n");
	}	
    }
    
    map = new int[maxdesc];       //alloc map of open descs!
    bzero(map,sizeof(int)*maxdesc);        
    
    
    if(port_id!=0)
    { 
        //open and init connector socket at which clients should
        //attempt to connect
 
	struct sockaddr_in serv_addr;
	if ( (confd = socket(AF_INET, SOCK_STREAM, 0)) < 0)
	{
	  perror("server: can't open stream socket\n");

	}
	
	bzero((char *) &serv_addr, sizeof(serv_addr));
	serv_addr.sin_family      = AF_INET;
	serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
	serv_addr.sin_port        = htons(port_id);
	
	//Bind the local address to socket

	if (bind(confd, (struct sockaddr *) &serv_addr,
		 sizeof(serv_addr)) < 0)
	{
	  perror("server: can't bind local address\n");

	}

	//use it as connector - i.e. tell kernel to look out for 
        //connection indications

	if(listen(confd, 5)<0)
	{
	    perror("server: can't listen\n");
	}
	FD_SET(confd,&read_before); //include in multiplex set
	
	map[confd]=1;               //confd is in use
	no_open++;
    }
    
}

// /////////////////////////////////////////////////////////////////
// DO_POLL - perform multiplexing
// /////////////////////////////////////////////////////////////////
int TCP::do_poll()
{
#ifdef TCP_DEBUG
    fprintf(stderr,"try select\n");
#endif /*TCP_DEBUG*/

    //calls to this method will not perform select() each time
    //but serve all ready fd's before select(). The class remembers
    //how many socket were ready in the last select() call (no_ready)
    //and which socket it served (pollpos) in the last invocation.

    //This avoids starvation of desc's with high socket id's

    pollpos++;
    
    if(no_ready<=0)
    {
	//since select modifies its multiplex set to include those
	//sockets ready for reading we wish to save our set in 
	//read_before and use the read_after set in the call

	read_after=read_before;	
	ex_after=read_before;
	while(1)
	{
	    no_ready=select(maxdesc+1,&read_after,NULL,&ex_after,NULL);
	    if(no_ready<=0)
	    {
		if(errno==EINTR) continue;	    
		perror("server: select failed\n");
		return -1;	    
	    } else break;
	    
	}
	

	pollpos=0;	    
    }

    //check each open socket in turn if it is included in the ready-set

    while(pollpos<maxdesc)
    {
	if(map[pollpos]==1)
	{	
	    if(FD_ISSET(pollpos,&ex_after)!=0)
	    {
#ifdef TCP_DEBUG
		fprintf(stderr,"Exception on socket %d\n",pollpos);
#endif /*TCP_DEBUG*/
		return (-pollpos);
	    }
	    else
		if(FD_ISSET(pollpos,&read_after)!=0)
		{
		    no_ready--;	    
		    if(pollpos==confd)
		    {
			//if the connector socket is ready for reading
			//this means a new connection
#ifdef TCP_DEBUG
			fprintf(stderr,"connection indication\n");
#endif /*TCP_DEBUG*/
			return 0;
		    }
		    
		    else
		    {
#ifdef TCP_DEBUG
			fprintf(stderr,"read indication\n");
#endif /*TCP_DEBUG*/
			return pollpos;	    
		    }
		    
		}
	}
	pollpos++;	
    }
    

    fprintf(stderr,"ERROR: this should'nt happen\n");  
    no_ready=0;
    
    return -1;    
}

int TCP::shutdown()
{
#ifdef TCP_DEBUG
    fprintf(stderr,"DESTRUCTING TCP\n");
#endif /*TCP_DEBUG*/
#ifdef TIME
  fprintf(stderr,"WRITE AVG %ld usec/msg \nREAD AVG %ld usec/msg\n",
	  t_write.avg,t_read.avg);
#endif
    
    //shut down
    for(int j =0;j<maxdesc;j++)
	if(map[j]==1) 
	{
	    do_close(j);
	    ::shutdown(j,2);
	}
    
    return 1;
}

/*
int main (int argc, char * argv[]){
  int IUT;
  tcp t(10000);
  IUT=t.connect_to(NULL,9999,"localhost");
  if(IUT<0) fprintf(stderr,"Not Connected\n");

  fprintf(stderr,"Connected\n");

  while(1) {
    sleep(3);
    fprintf(stderr,"%p\n",t.sendBuf);
    
    memcpy(t.sendBuf,(char*)&testIGrasp,sizeof(int));
    fprintf(stderr,"LINE: %d\n", __LINE__);

    
    if(t.do_write(IUT,2*sizeof(int)) <0) 
      fprintf(stderr,"Problem LINE: %d\n", __LINE__);
    //memcpy(t.sendBuf+sizeof(int),(char*)&testIGrasp,sizeof(int)); 
    usleep(10000);
    memcpy(t.sendBuf,(char*)&testIRelease,sizeof(int));
    if(t.do_write(IUT,2*sizeof(int)) <0) 
      fprintf(stderr,"Problem LINE: %d\n", __LINE__);
  }
}
*/





/* ------------------------------------------------------*/ 

/* SAP BEGIN */ 


int SAPTCP::readV(int socketid, int size){
  return do_read(socketid,receiveValueBuf,size);
}
int SAPTCP::writeV(int socketid,int size){
  return do_write(socketid,sendValueBuf,size);
}
void SAPTCP::putInt32(char*buf,int value) {
  int tmpsize=htonl(value);
  memcpy(buf,(char*)&tmpsize,sizelen);
}

int SAPTCP::getInt32(char*buf) {
  int value;
  memcpy((char*)&value,buf,sizelen);
  return ntohl(value);
}

int SAPTCP::writeLV(int socketid,int size)
{
  putInt32(realLVSendBuf,size);
  int leftover=size+sizelen;
  int written = TCP::do_write(socketid,realLVSendBuf,leftover);
  return written;    
}

// /////////////////////////////////////////////////////////////////
// DO_READ reads a message from socketid to buf
// /////////////////////////////////////////////////////////////////

int SAPTCP::readLV(int socketid)
{

  int did_read=0; 

  //read length field
  did_read=TCP::do_read(socketid,realLVReceiveBuf,sizelen);
  if(did_read!=sizelen) {
    fprintf(stderr, "Warning: asked for %d, got %d\n", sizelen,did_read);
    return -1;
  }
  int leftover=getInt32(realLVReceiveBuf);

  if(leftover>bufsize) {
    fprintf(stderr, "Error: buffer exceeded: messagelen %d \n", leftover);
    return -1;
  }
  //read the real message
  did_read=do_read(socketid,receiveValueBuf,leftover);
  if(did_read!=leftover) {
    fprintf(stderr, "Warning: asked for %d, got %d\n", sizelen,did_read);
    return -1;
  }
  return did_read;
}



int SAPTCP::writeTLV(int socketid,int type, int size){
  putInt32(realTLVSendBuf,type);
  putInt32(realLVSendBuf,size);
  int totalsize=size+sizetype+sizelen;
  int written = TCP::do_write(socketid,realTLVSendBuf,totalsize);
  return written;    
}
int SAPTCP::readTLV(int socketid, int & type) {

  int did_read=0; 
  //read type field
  did_read=TCP::do_read(socketid,realTLVReceiveBuf,sizetype);
  if(did_read!=sizetype) {
    fprintf(stderr, "Warning: asked for %d, got %d\n", sizetype,did_read);
    return -1;
  }
  type=getInt32(realTLVReceiveBuf);

  //read Length field
  did_read=TCP::do_read(socketid,realLVReceiveBuf,sizelen);
  if(did_read!=sizelen) {
    fprintf(stderr, "Warning: asked for %d, got %d\n", sizelen,did_read);
    return -1;
  }

  int leftover=getInt32(realLVReceiveBuf);  
  if(leftover>bufsize) {
    fprintf(stderr, "Error: buffer exceeded: messagelen %d \n", leftover);
    return -1;
  }
  // read value
  did_read=do_read(socketid,receiveValueBuf,leftover);
  if(did_read!=leftover) {
    fprintf(stderr, "Warning: asked for %d, got %d\n", sizelen,did_read);
    return -1;
  }
  return did_read;
}





int SAPTCP::do_accept(connect_info * ci)
{
  int newfd;
  newfd=TCP::do_accept();
  if(newfd<0) return newfd;
    
  //read connect_info message sent by client
  if(ci!=NULL) {
    if(TCP::do_read(newfd,(char*)ci,sizeof(ci))<0)
      return -1;	
  }
  return newfd;
  
}
int SAPTCP::connect_to(connect_info * ci, int port_id, const char* hostname){
  int newfd;
  newfd=TCP::connect_to( port_id, hostname);
  if(ci!=NULL)
    TCP::do_write(newfd,(char*)ci,sizeof(connect_info));
  
  return newfd;
}

SAPTCP::~SAPTCP()
{
  delete [] realTLVSendBuf;
  delete [] realTLVReceiveBuf;
  //  ~TCP()
}

void SAPTCP::makeBufs(int bufsize) {
  this->bufsize=bufsize;
  this->sizelen=sizeof(int);
  this->sizetype=sizeof(int);
  realTLVSendBuf=new char [bufsize+sizelen+sizetype];
  realTLVReceiveBuf=new char [bufsize+sizelen+sizetype];
  
  realLVSendBuf=realTLVSendBuf+sizetype; 
  realLVReceiveBuf=realTLVReceiveBuf+sizetype;

  sendValueBuf=realTLVSendBuf+sizetype+sizelen; 
  receiveValueBuf=realTLVReceiveBuf+sizetype+sizelen;

 
}

SAPTCP::SAPTCP(int bufsize):  TCP(0){
  makeBufs(bufsize);
}
SAPTCP::SAPTCP(int bufsize, int port_id):  TCP(port_id) {
  makeBufs(bufsize);
}


/*
int main (int argc, char * argv[]){
  int IUT;
  tcp t(10000);
  IUT=t.connect_to(NULL,9999,"localhost");
  if(IUT<0) fprintf(stderr,"Not Connected\n");

  fprintf(stderr,"Connected\n");

  while(1) {
    sleep(3);
    fprintf(stderr,"%p\n",t.sendBuf);
    
    memcpy(t.sendBuf,(char*)&testIGrasp,sizeof(int));
    fprintf(stderr,"LINE: %d\n", __LINE__);

    
    if(t.do_write(IUT,2*sizeof(int)) <0) 
      fprintf(stderr,"Problem LINE: %d\n", __LINE__);
    //memcpy(t.sendBuf+sizeof(int),(char*)&testIGrasp,sizeof(int)); 
    usleep(10000);
    memcpy(t.sendBuf,(char*)&testIRelease,sizeof(int));
    if(t.do_write(IUT,2*sizeof(int)) <0) 
      fprintf(stderr,"Problem LINE: %d\n", __LINE__);
  }
}
*/
/*
//test LV
int main (int argc, char * argv[]){
  int IUT;
  SAPTCP t(10000);
  IUT=t.connect_to(NULL,9999,"localhost");
  if(IUT<0) fprintf(stderr,"Not Connected\n");

  fprintf(stderr,"Connected\n");

  for(int i=-(2<<10);i<(2<<10);i++){
    int w=htonl(i);
    memcpy(t.sendBuf,(char*)&w,sizeof(int));
    if(t.do_write(IUT,2*sizeof(int)) <0) 
      fprintf(stderr,"Problem LINE: %d\n", __LINE__);
    if(t.do_read(IUT) <0) 
      fprintf(stderr,"Problem LINE: %d\n", __LINE__);
    int r=((int*) t.receiveBuf)[0];
    printf("send %d, received %d (raw), ntoh=%d\n",i,r,ntohl(r));
    if(ntohl(r)!=i) { printf("HMMMM\n");exit(0);}
  }
}
*/

/*
int TCP::do_read(int socketid, char * buf,int size)
int TCP::do_write(int socketid,char * buf, int size)
*/


/*
//test TLV
int main (int argc, char * argv[]){
  int IUT;
  SAPTCP t(10000);
  IUT=t.connect_to(NULL,9999,"localhost");
  if(IUT<0) fprintf(stderr,"Not Connected\n");

  fprintf(stderr,"Connected\n");

  for(int j=-(2<<10);j<(2<<10);j++){
    for(int i=-(2<<10);i<(2<<10);i++){
      int w=htonl(i);
      memcpy(t.sendValueBuf,(char*)&w,sizeof(int));
      if(t.writeTLV(IUT,j,2*sizeof(int)) <0) 
	fprintf(stderr,"Problem LINE: %d\n", __LINE__);
      int rtype=-99;int rcvd;
      if((rcvd=t.readTLV(IUT,rtype)) <0) 
	fprintf(stderr,"Problem LINE: %d\n", __LINE__);
      int r=((int*) t.receiveValueBuf)[0];
      printf("send type %d, value %d, received type %d len %d value %d (raw), ntoh=%d\n",j,i,rtype,rcvd,r,ntohl(r));
      if((ntohl(r))!=i||(rtype!=j)) { printf("HMMMM\n");exit(0);}
    }
  }
}
*/
