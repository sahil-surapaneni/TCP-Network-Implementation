JCC = javac

JFLAGS = -g

default: src/TCPend.class src/TCPreceiver.class src/TCPsender.class

src/TCPend.class: src/TCPend.java
	cd src; $(JCC) $(JFLAGS) TCPend.java

src/TCPreceiver.class: src/TCPreceiver.java
	cd src; $(JCC) $(JFLAGS) TCPreceiver.java

src/TCPsender.class: src/TCPsender.java
	cd src; $(JCC) $(JFLAGS) TCPsender.java
