d<-scan("pingpong.log")
pingpong <- matrix(d, ncol=3, byrow=T)
mtu <- 1000;

ping <- pingpong[,2] - (pingpong[,1]-1)*410*mtu
pong <- pingpong[,3] - (pingpong[,1]-1)*410*mtu

ping1k <- ping/1000
pong1k <- pong/1000

# sample plots:
postscript(file="pingpong.eps", onefile=T)
plot(pong1k-ping1k, xlab="instance, #", ylab="response time, milliseconds")
abline(coef(line(pong1k-ping1k)))

postscript(file="ping.eps", onefile=T)
plot(ping1k, xlab="instance, #", ylab="ping time, milliseconds")

postscript(file="pong.eps", onefile=T)
plot(pong1k, xlab="instance, #", ylab="pong time, milliseconds")


# Histograms:
postscript(file="pingHist.eps", onefile=T)
hist(ping1k, xlab="ping time, ms", main="Histogram of ping", breaks=10, col=8)

postscript(file="pongHist.eps", onefile=T)
hist(pong1k, xlab="pong time, ms", main="Histogram of pong", breaks=10, col=8)

postscript(file="pingpongHist.eps", onefile=T)
hist(pong1k-ping1k, xlab="time difference, ms", main="Histogram of pong-ping", breaks=15, col=8)

# standard t-test:

diff <- pong1k - ping1k
diffref <- diff*0 + 200
t.test(diff, diffref)

# build linear model:
model <- lm(pong1k~ping1k)
summary(model)

# extract residuals to linear model:
res <- resid(model)
ponghat <- fitted(model)

postscript(file="pingpongStack.eps", onefile=T)
stripchart(pong1k~ping1k, method="stack", vertical=T, pch=1, xlab="ping time, ms", ylab="pong time, ms")

# normality tests of residuals (residuals must be structureless vs any data):

postscript(file="pingpongQQ.eps", onefile=T)
qqnorm(res)
qqline(res, col="gray")

# residual plots:
postscript(file="pongRes.eps", onefile=T)
stripchart(res ~ pong1k, vert=T, method="stack")
plot(pong1k, res, xlab="Pong time, ms", ylab="Residuals", main="Residuals vs Pong")
abline(h=0)

postscript(file="pongHatRes.eps", onefile=T)
plot(model, which=1, sub.caption="pong time, ms")
#plot(ponghat, res, xlab="Fitted pong time, ms", ylab="Residuals", main="Residuals vs Fitted")
#abline(h=0)

postscript(file="pongQQ.eps", onefile=T)
plot(model, which=2)

postscript(file="pongHatResStand.eps", onefile=T)
plot(model, which=3, sub.caption="pong time, ms")

quit()

# analysis of variance:

vping <- (pingpong[,2]- (pingpong[,1]-1)*410*mtu) / 1000
vpong <- (pingpong[,3]- (pingpong[,1]-1)*410*mtu) / 1000
vpongref <- vping + 200

aov1 <- aov(vpongref~vpong)
summary(aov1)
res <- resid(aov1)

# residual plots:
postscript(file="pongRes.eps", onefile=T)
stripchart(res ~ vpong, vert=T, method="stack")
plot(vpong, res, xlab="Pong time, ms", ylab="Residuals", main="Residuals vs Pong")
abline(h=0)

postscript(file="pongHatRes.eps", onefile=T)
plot(aov1, which=1, sub.caption="pong time, ms")
#plot(vpongref, res, xlab="Pong reference time, ms", ylab="Residuals", main="Residuals vs Fitted")
#abline(h=0)

postscript(file="pongQQ.eps", onefile=T)
plot(aov1, which=2)

postscript(file="pongHatResStand.eps", onefile=T)
plot(aov1, which=3, sub.caption="pong time, ms")

# lines(c(0,length(d)), c(199000, 199000))
# lines(c(0,length(d)), c(201000, 201000))
