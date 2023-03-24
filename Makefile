PREFIX ?= /usr/local

all:
	mvn package

clean:
	mvn clean

install: all
	install -dm755 $(DESTDIR)$(PREFIX)/share/ConvertWithMoss
	cp target/lib/convertwithmoss*.jar $(DESTDIR)$(PREFIX)/share/ConvertWithMoss/convertwithmoss.jar
	cp target/lib/*-linux.jar $(DESTDIR)$(PREFIX)/share/ConvertWithMoss/
	cp target/lib/uitools-*.jar $(DESTDIR)$(PREFIX)/share/ConvertWithMoss/
	install -Dm644 linux/de.mossgrabers.ConvertWithMoss.desktop -t $(DESTDIR)$(PREFIX)/share/applications/
	install -Dm644 linux/de.mossgrabers.ConvertWithMoss.appdata.xml -t $(DESTDIR)$(PREFIX)/share/metainfo/
	install -Dm644 icons/convertwithmoss.png -t $(DESTDIR)$(PREFIX)/share/pixmaps/
	install -Dm755 linux/convertwithmoss.sh $(DESTDIR)$(PREFIX)/bin/convertwithmoss

.PHONY: all clean install
