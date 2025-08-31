package max.chess.models.pieces.magic_bitboard;

import max.chess.engine.movegen.utils.OrthogonalMoveUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class ComputeRookMagicNumbers {
    @Test
    public void computeRookMagicNumbers() {
        Map<Integer, Map<Long, Long>> originalMoves = new HashMap<>(RookMovesLookUp.ORIGINAL_MOVES);

        // We start from the original moves and we now want to encode each position on a given number of bits
        // There should be no collision where the index determined is already used

        Duration maximumDurationPerSquare = Duration.ofSeconds(10);

        List<Long> magicNumbers = new ArrayList<>(64);
        List<Integer> magicNumbersRightShifts = new ArrayList<>(64);

        Random rand = new Random();

        for (int i = 0; i < 64; i++) {
            if (magicNumbers.size() < i) {
                System.out.println("At least one magic number not found. Aborting.");
                break;
            }
            // Let's start with 15 bits (we should be able to reduce down to 12)
            int numberOfBits = 63;

            Map<Long, Long> movesForSquare = originalMoves.get(i);
            Instant startOfSquare = Instant.now();
            long bestMagicNumber = 0;
            int bestMagicNumberBits = 64;
            while (bestMagicNumberBits > 12 && Duration.between(startOfSquare, Instant.now()).compareTo(maximumDurationPerSquare) < 0) {
                int rightShift = 64 - numberOfBits;

                long magicNumber = rand.nextLong() & rand.nextLong() & rand.nextLong();
                Map<Long, Long> indexesEncountered = new HashMap<>(movesForSquare.size());
                boolean found = true;
                for (Map.Entry<Long, Long> entry : movesForSquare.entrySet()) {
                    long blockerBB = entry.getKey() & OrthogonalMoveUtils.orthogonals[i];
                    long actualValue = entry.getValue();
                    long index = RookMovesLookUp.buildIndex(blockerBB, magicNumber, rightShift);
                    if (indexesEncountered.containsKey(index) && indexesEncountered.get(index) != actualValue) {
                        // Collision !
                        found = false;
                        break;
                    }
                    indexesEncountered.put(index, actualValue);
                }

                if(found) {
                    bestMagicNumber = magicNumber;
                    bestMagicNumberBits = numberOfBits;
                    numberOfBits--;
                }
            }

            if (bestMagicNumber == 0) {
                System.out.println("DID NOT FIND ANY MAGIC NUMBER FOR SQUARE "+i+" with "+numberOfBits+" bits in under "+maximumDurationPerSquare+" ! ABORTING...");
                break;
            } else {
                magicNumbers.add(bestMagicNumber);
                magicNumbersRightShifts.add(64 - bestMagicNumberBits);
                System.out.println("Magic number found for position " + i + " on " + bestMagicNumberBits + " bits !");
                System.out.println("Number: " + bestMagicNumber);
            }
        }

        if (magicNumbers.size() == 64) {
            System.out.println("Rook Magic Bitboard ready ; size used : "+RookMovesLookUp.getMagicBitboardSize(magicNumbersRightShifts)+ "B");
            System.out.println("MAGIC NUMBERS FOR ROOK:");
            System.out.println("{" + magicNumbers.stream().map(number -> number + "L").collect(Collectors.joining(", ")) + "};");
            System.out.println("RIGHT SHIFTS FOR ROOK:");
            System.out.println("{" + magicNumbersRightShifts.stream().map(Object::toString).collect(Collectors.joining(", ")) + "};");
        }
    }

    @Test
    public void enhanceRookMagicNumbers() {
        Map<Integer, Map<Long, Long>> originalMoves = new HashMap<>(RookMovesLookUp.ORIGINAL_MOVES);

        // We start from the original moves and we now want to encode each position on a given number of bits
        // There should be no collision where the index determined is already used

        Duration maximumDurationPerSquare = Duration.ofSeconds(60);

        List<Long> magicNumbers = RookMovesLookUp.MAGIC_NUMBERS;
        List<Long> newMagicNumbers = new ArrayList<>(64);
        List<Integer> magicNumbersRightShifts = RookMovesLookUp.RIGHT_SHIFTS;
        List<Integer> newMagicNumberRightShifts = new ArrayList<>(64);

        long initialBitboardSize = RookMovesLookUp.getMagicBitboardSize(RookMovesLookUp.RIGHT_SHIFTS);

        Random rand = new Random();

        for (int i = 0; i < 64; i++) {
            if (magicNumbers.size() < i) {
                System.out.println("At least one magic number not found. Aborting.");
                break;
            }
            // Let's start with 15 bits (we should be able to reduce down to 12)
            int bestMagicNumberBits = 64 - magicNumbersRightShifts.get(i);
            int numberOfBits = bestMagicNumberBits - 1;

            Map<Long, Long> movesForSquare = originalMoves.get(i);
            Instant startOfSquare = Instant.now();
            long bestMagicNumber = magicNumbers.get(i);
            while (Duration.between(startOfSquare, Instant.now()).compareTo(maximumDurationPerSquare) < 0) {
                int rightShift = 64 - numberOfBits;

                long magicNumber = rand.nextLong() & rand.nextLong() & rand.nextLong();
                Map<Long, Long> indexesEncountered = new HashMap<>(movesForSquare.size());
                boolean found = true;
                for (Map.Entry<Long, Long> entry : movesForSquare.entrySet()) {
                    long blockerBB = entry.getKey() & OrthogonalMoveUtils.orthogonals[i];
                    long actualValue = entry.getValue();
                    long index = RookMovesLookUp.buildIndex(blockerBB, magicNumber, rightShift);
                    if (indexesEncountered.containsKey(index) && indexesEncountered.get(index) != actualValue) {
                        // Collision !
                        found = false;
                        break;
                    }
                    indexesEncountered.put(index, actualValue);
                }

                if(found) {
                    bestMagicNumber = magicNumber;
                    bestMagicNumberBits = numberOfBits;
                    numberOfBits--;
                }
            }

            if (64 - bestMagicNumberBits == magicNumbersRightShifts.get(i)) {
                System.out.println("DID NOT FIND ANY BETTER MAGIC NUMBER FOR SQUARE "+i+" with "+numberOfBits+" bits in under "+maximumDurationPerSquare+" ! ");
            } else {
                System.out.println("Better Magic number found for position " + i + " on " + bestMagicNumberBits + " bits compared to "+(64-magicNumbersRightShifts.get(i))+" before !");
                System.out.println("Number: " + bestMagicNumber);
            }
            newMagicNumbers.add(bestMagicNumber);
            newMagicNumberRightShifts.add(64 - bestMagicNumberBits);
        }

        if (magicNumbers.size() == 64) {
            System.out.println("Rook Magic Bitboard ready !");
            System.out.println("Size reduced to : "+RookMovesLookUp.getMagicBitboardSize(newMagicNumberRightShifts)+ "B");
            System.out.println("Size was : "+initialBitboardSize+ "B before !");
            System.out.println("MAGIC NUMBERS FOR ROOK:");
            System.out.println("{" + newMagicNumbers.stream().map(number -> number + "L").collect(Collectors.joining(", ")) + "};");
            System.out.println("RIGHT SHIFTS FOR ROOK:");
            System.out.println("{" + newMagicNumberRightShifts.stream().map(Object::toString).collect(Collectors.joining(", ")) + "};");
        }
    }
}
