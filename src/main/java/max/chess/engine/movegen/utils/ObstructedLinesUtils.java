package max.chess.engine.movegen.utils;

import max.chess.engine.common.Position;

public final class ObstructedLinesUtils {
    public static final long[][] OBSTRUCTED_BB = new long[64][64];

    static {
        fillObstructedBB();
    }

    public static void warmUp() {
        // To init static block
    }

    private static void fillObstructedBB() {
        for(int i = 0; i < 64; i++) {
            for(int j = 0; j < 64; j++) {
                if(i == j) {
                    OBSTRUCTED_BB[i][j] = 0;
                    continue;
                }

                Position position1 = Position.of(i);
                Position position2 = Position.of(j);
                long obstructedBB = 0;

                if(position1.getX() == position2.getX()) {
                    if (position1.getY() > position2.getY()) {
                        // detecting if p1 is "above" p2
                        Position currentPosition = Position.of(position1.getX(), position1.getY()-1);
                        while(!currentPosition.equals(position2)) {
                            obstructedBB |= 1L << currentPosition.getFlatIndex();
                            currentPosition = Position.of(currentPosition.getX(), currentPosition.getY()-1);
                        }
                    } else if (position1.getY() < position2.getY()) {
                        // detecting if p1 is "below" p2
                        Position currentPosition = Position.of(position1.getX(), position1.getY()+1);
                        while(!currentPosition.equals(position2)) {
                            obstructedBB |= 1L << currentPosition.getFlatIndex();
                            currentPosition = Position.of(currentPosition.getX(), currentPosition.getY()+1);
                        }
                    }
                } else if(position1.getY() == position2.getY()) {
                    if (position1.getX() > position2.getX()) {
                        // Detecting if p1 is "right" to p2
                        Position currentPosition = Position.of(position1.getX()-1, position1.getY());
                        while(!currentPosition.equals(position2)) {
                            obstructedBB |= 1L << currentPosition.getFlatIndex();
                            currentPosition = Position.of(currentPosition.getX()-1, currentPosition.getY());
                        }
                    }  else if (position1.getX() < position2.getX()) {
                        // detecting if p1 is "left" to p2
                        Position currentPosition = Position.of(position1.getX()+1, position1.getY());
                        while(!currentPosition.equals(position2)) {
                            obstructedBB |= 1L << currentPosition.getFlatIndex();
                            currentPosition = Position.of(currentPosition.getX()+1, currentPosition.getY());
                        }
                    }
                } else if(Math.abs(position1.getX() - position2.getX()) == Math.abs(position1.getY() - position2.getY())) {
                    // We are on a diagonal !
                    if(position1.getX() - position2.getX() > 0 && position1.getY() - position2.getY() > 0) {
                        // p1 is "up-right" compared to p2
                        Position currentPosition = Position.of(position1.getX()-1, position1.getY()-1);
                        while(!currentPosition.equals(position2)) {
                            obstructedBB |= 1L << currentPosition.getFlatIndex();
                            currentPosition = Position.of(currentPosition.getX()-1, currentPosition.getY()-1);
                        }
                    } else if(position1.getX() - position2.getX() > 0 && position1.getY() - position2.getY() < 0) {
                        // p1 is "down-right" compared to p2
                        Position currentPosition = Position.of(position1.getX()-1, position1.getY()+1);
                        while(!currentPosition.equals(position2)) {
                            obstructedBB |= 1L << currentPosition.getFlatIndex();
                            currentPosition = Position.of(currentPosition.getX()-1, currentPosition.getY()+1);
                        }
                    } else if(position1.getX() - position2.getX() < 0 && position1.getY() - position2.getY() < 0) {
                        // p1 is "down-left" compared to p2
                        Position currentPosition = Position.of(position1.getX()+1, position1.getY()+1);
                        while(!currentPosition.equals(position2)) {
                            obstructedBB |= 1L << currentPosition.getFlatIndex();
                            currentPosition = Position.of(currentPosition.getX()+1, currentPosition.getY()+1);
                        }
                    } else if(position1.getX() - position2.getX() < 0 && position1.getY() - position2.getY() > 0) {
                        // p1 is "up-left" compared to p2
                        Position currentPosition = Position.of(position1.getX()+1, position1.getY()-1);
                        while(!currentPosition.equals(position2)) {
                            obstructedBB |= 1L << currentPosition.getFlatIndex();
                            currentPosition = Position.of(currentPosition.getX()+1, currentPosition.getY()-1);
                        }
                    }
                }

                OBSTRUCTED_BB[i][j] = obstructedBB;
            }
        }
    }
}
