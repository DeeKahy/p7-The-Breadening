d<-scan("response.log")
pingpong <- matrix(d, ncol=3, byrow=T)
mtu <- 1000;

ping <- pingpong[,2] - (pingpong[,1]-1)*410*mtu
pong <- pingpong[,3] - (pingpong[,1]-1)*410*mtu

ping1k <- ping/1000
pong1k <- pong/1000

# sample plots:
postscript(file="response.eps", onefile=T)
plot(pong1k-ping1k, xlab="instance, #", ylab="time difference between ping and pong, milliseconds")
abline(coef(line(pong1k-ping1k)))

# Histograms:
postscript(file="responseHist.eps", onefile=T)
hist(pong1k-ping1k, xlab="time difference between ping and pong, ms", main="Histogram of pong-ping response", breaks=15, col=8)

# Student's t-Test:

diff <- pong1k-ping1k
diffref <- diff*0
t.test(diff, diffref)
