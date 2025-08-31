package max.chess;

import max.chess.engine.uci.UciEngine;
import max.chess.engine.uci.UciEngineImpl;
import max.chess.engine.uci.UciServer;

public class Main {
    public static void main(String[] args) {
        UciEngine engine = new UciEngineImpl(); // your implementation
        new UciServer("MaxBot", "Max", engine).run();
    }}