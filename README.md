Welcome to my chess engine written in Java

# Current stack
JDK24 - GraalVM

# Currently done
- Movegen is solid (see benchmarks) and fully working

# Next steps
- Implement search function to get the bot to play games

# Current benchmarks
## Perft
### Standard board
#### perft (depth) 7
- Stockfish: 220M nps
- This engine: 160M nps
  - Note that this engine is *not* streaming moves for perft compared to other top engines ; 10% perf increase are expected if it's done one day - does not impact actual ELO

# Build
Note that while PEXT CPU instruction is not used so far (overhead of native call measured as not worth it), if you want to link it with this project:

## Linux/mac (Intel)
```
gcc -O3 -mbmi2 -fPIC -shared pext.c -o liblextract.so        # Linux
clang -O3 -mbmi2 -fPIC -shared pext.c -o libextract.dylib     # macOS Intel
```

## Windows (MSVC x64):
```
cl /O2 /LD pext.c /Fe:extract.dll
```