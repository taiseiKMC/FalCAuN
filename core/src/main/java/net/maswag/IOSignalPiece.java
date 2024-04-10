package net.maswag;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * A pair of input and output signals at one time step.
 *
 * @param <I> the type of the input and output signals
 */
@Getter @AllArgsConstructor
public class IOSignalPiece<I> {
    final private I inputSignal, outputSignal;
}
