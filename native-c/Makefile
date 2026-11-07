CC ?= cc
CFLAGS ?= -O2 -std=c11 -Wall -Wextra -Wpedantic -Wconversion -Iinclude -fsanitize=address,undefined
SRC = src/b64.c src/sha256.c src/link.c src/cidr.c

test: $(SRC) test/test.c include/fqc.h
	$(CC) $(CFLAGS) $(SRC) test/test.c -o fqc_test
	./fqc_test

clean:
	rm -f fqc_test
