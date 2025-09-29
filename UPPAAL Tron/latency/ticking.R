data <- scan("ticking.log")
mtu <- data[1]
ticks <- matrix(tail(data, length(data)-1), ncol=2, byrow=T)
rt <- ticks[,1]
mt <- ticks[,2]*250*mtu-50*mtu
d <- rt - mt
postscript(file="ticking.eps", onefile=T)
plot(d/1000, xlab="instance, #", ylab="deviation, milliseconds")

postscript(file="tickingHist.eps", onefile=T)
hist(d/1000, xlab="tick time, ms", main="Histogram of tick time", breaks=10, col=8)
