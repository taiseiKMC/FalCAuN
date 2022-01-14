package org.group_mmm;

import java.util.*;
import java.util.function.Function;

/**
 * Adaptive updater of STL/LTL formulas
 *
 * @author Junya Shijubo
 * @see BlackBoxVerifier
 * @see SimulinkVerifier
 */

public class AdaptiveSTLList extends AbstractAdaptiveSTLUpdater {
    private final List<STLCost> STLproperties;
    private final List<STLCost> targetSTLs;
    private final List<List<STLCost>> strengthenedSTLproperties;

    private final List<List<STLCost>> candidateSTLproperties;
    private final List<List<IntervalSTL>> intervalSTLproperties;
    private final List<STLCost> falsifiedSTLproperties;

    public AdaptiveSTLList() {
        this(Collections.emptySet());
    }

    public AdaptiveSTLList(STLCost propertyOracle) {
        this(Collections.singleton(propertyOracle));
    }

    /**
     * @param STLproperties The list of STL/LTL formulas to verify
     */
    public AdaptiveSTLList(Collection<? extends STLCost> STLproperties) {
        // list of STL/LTL formulas to be model-checked
        // target STL/LTL formulas to adaptively strengthen
        this.targetSTLs = new ArrayList<>(STLproperties);
        // list of strengthened STL/LTL formulas to be model-checked
        this.strengthenedSTLproperties = new ArrayList<>();

        this.candidateSTLproperties = new ArrayList<>();
        this.intervalSTLproperties = new ArrayList<>();
        for(int targetIdx = 0; targetIdx < targetSTLs.size(); targetIdx++) {
            // syntactically strengthen targetSTLs
            this.strengthenedSTLproperties.add(new ArrayList<>());
            this.candidateSTLproperties.add(generateStrengthenedSTL(targetSTLs.get(targetIdx)));
            if (this.candidateSTLproperties.get(targetIdx).size() > 0) {
                // if there exists any candidate, add one to STLproperties and model-check against it.
                this.strengthenedSTLproperties.get(targetIdx).add(0, this.candidateSTLproperties.get(targetIdx).remove(0));
            }
            // change intervals of temporal operators in targetSTL
            this.intervalSTLproperties.add(initializeIntervalSTLproperties(targetSTLs.get(targetIdx)));
            for(int j = 0; j < this.intervalSTLproperties.get(targetIdx).size(); j++) {
                this.strengthenedSTLproperties.get(targetIdx).add(j, intervalSTLproperties.get(targetIdx).get(j).strengthInit());
            }
        }

        this.falsifiedSTLproperties = new ArrayList<>();

        this.STLproperties = new ArrayList<>();
        this.strengthenedSTLproperties.forEach(this.STLproperties::addAll);
        this.STLproperties.addAll(this.targetSTLs);
        System.out.println("STLproperties ::=");
        this.STLproperties.forEach(s -> System.out.println("STL: " + s.toString()));
    }

    @Override
    public List<STLCost> getSTLProperties() {
        return this.STLproperties;
    }

    @Override
    protected void notifyFalsifiedProperty(List<Integer> falsifiedIndices) {
        // remove all STL/LTL formula that is falsified from STLproperties
        falsifiedIndices.sort(Collections.reverseOrder());
        List<STLCost> falsifiedSTLs = new ArrayList<>();
        for (int falsifiedIdx : falsifiedIndices) {
            STLCost falsifiedSTL = this.STLproperties.remove(falsifiedIdx);
            this.falsifiedSTLproperties.add(falsifiedSTL);
            falsifiedSTLs.add(falsifiedSTL);
        }

        // if any targetSTL is falsified, remove all strengthened properties generated from the STL
        falsifiedIndices.sort(Collections.reverseOrder());
        for (STLCost falsifiedSTL : falsifiedSTLs) {
            boolean isTarget = false;
            for (int targetIdx = this.targetSTLs.size() - 1; targetIdx >= 0; targetIdx--) {
                if (falsifiedSTL.equals(targetSTLs.get(targetIdx))) {
                    // if a targetSTL is falsified, remove it
                    isTarget = true;
                    System.out.println("STLproperty is falsified: " + falsifiedSTL);
                    this.targetSTLs.remove(targetIdx);
                    this.candidateSTLproperties.remove(targetIdx);
                    this.intervalSTLproperties.remove(targetIdx);
                    this.strengthenedSTLproperties.remove(targetIdx);
                    if (this.targetSTLs.size() == 0) {
                        System.out.println("All STLproperties are falsified");
                        this.falsifiedSTLproperties.addAll(this.STLproperties);
                        this.STLproperties.clear();
                        return;
                    }
                }
            }
            if (!isTarget) {
                // when the falsifiedSTL is a strengthened property
                System.out.println("Adaptive STLproperty is falsified: " + falsifiedSTL);
                for (int targetIdx = 0; targetIdx < this.strengthenedSTLproperties.size(); targetIdx++) {
                    int pos = this.strengthenedSTLproperties.get(targetIdx).indexOf(falsifiedSTL);
                    if (pos != -1) {
                        this.strengthenedSTLproperties.get(targetIdx).remove(pos);
                        if (pos < this.intervalSTLproperties.get(targetIdx).size()) {
                            // if the falsified formula is generated by changing intervals, change intervals to make it stronger
                            STLCost next = this.intervalSTLproperties.get(targetIdx).get(pos).nextStrengthedSTL();
                            if (Objects.isNull(next)) {
                                this.intervalSTLproperties.get(targetIdx).remove(pos);
                            } else {
                                this.strengthenedSTLproperties.get(targetIdx).add(pos, next);
                                System.out.println("Adaptive STLproperty(interval) is added: " + next.toString());
                            }
                        } else {
                            // pick a next STL/LTL formula from candidateSTLproperties
                            if (this.candidateSTLproperties.get(targetIdx).size() > 0) {
                                STLCost newSTL = nextStrengthenedSTL(targetIdx);
                                this.strengthenedSTLproperties.get(targetIdx).add(pos, newSTL);
                                System.out.println("Adaptive STLproperty(other) is added: " + newSTL.toString());
                            }
                        }
                    }
                }
            }

        }

        this.STLproperties.clear();
        this.strengthenedSTLproperties.forEach(this.STLproperties::addAll);
        this.STLproperties.addAll(this.targetSTLs);
        System.out.println("Adaptive STLproperties ::");
        this.STLproperties.forEach(s -> System.out.println("STL: " + s.toString()));
    }

    /**
     * generate syntactically strengthened STL formulas
     * @param targetStl a target STL/LTL formula
     * @return list of strengthened formulas
     */
    private List<STLCost> generateStrengthenedSTL(STLCost targetStl) {
        return strengthenSTL(targetStl);
    }

    /**
     * list up intervals of temporal operators in target STL formulas that can be strengthened
     * @param targetSTL a target STL/LTL formula
     * @return list of
     */
    private List<IntervalSTL> initializeIntervalSTLproperties(STLCost targetSTL) {
        return findIntervalSTL(targetSTL, (s) -> s);
    }

    /**
     * find intervals that can be strengthened
     * @param stl target STL formula
     * @param frame outer frame of param 'stl'
     * @return list of {@link IntervalSTL} object
     */
    private List<IntervalSTL> findIntervalSTL(STLCost stl, Function<STLCost, STLCost> frame) {
        // search STLSub and STLNext recursively
        if (stl instanceof STLOr) {
            List<STLCost> subFmls = ((STLOr) stl).getSubFmls();
            List<IntervalSTL> ret = new ArrayList<>();

            ret.addAll(findIntervalSTL(subFmls.get(0), (s) -> frame.apply(new STLOr(s, subFmls.get(1)))));
            ret.addAll(findIntervalSTL(subFmls.get(1), (s) -> frame.apply(new STLOr(subFmls.get(0), s))));
            return ret;
        }
        if (stl instanceof STLAnd) {
            List<STLCost> subFmls = ((STLAnd) stl).getSubFmls();
            List<IntervalSTL> ret = new ArrayList<>();

            ret.addAll(findIntervalSTL(subFmls.get(0), (s) -> frame.apply(new STLAnd(s, subFmls.get(1)))));
            ret.addAll(findIntervalSTL(subFmls.get(1), (s) -> frame.apply(new STLAnd(subFmls.get(0), s))));
            return ret;
        }
        if (stl instanceof STLGlobal) {
            STLCost subFml = ((STLGlobal) stl).getSubFml();
            List<IntervalSTL> ret = new ArrayList<>();
            ret.addAll(findIntervalSTL(subFml, (s) -> frame.apply(new STLGlobal(s))));
            return ret;
        }
        if (stl instanceof STLSub) {
            STLCost subFml = ((STLSub) stl).getSubFml();
            List<IntervalSTL> ret = new ArrayList<>();
            ret.add(new IntervalSTL((STLSub) stl, frame));
            return ret;
        }
        if (stl instanceof STLNext) {
            STLCost subFml = ((STLNext) stl).getSubFml();
            List<IntervalSTL> ret = new ArrayList<>();
            ret.add(new IntervalSTL(new STLSub(new STLGlobal(subFml),1,1), frame));
            return ret;
        }
        return new ArrayList<>();
    }

    /**
     * syntactically strengthen an STL/LTL formula
     * @param stl a target STL formula to be strengthen
     * @return list of {@link STLCost} objects
     */
    private static List<STLCost> strengthenSTL(STLCost stl) {
        if (stl instanceof STLOr) {
            List<STLCost> subFmls = ((STLOr) stl).getSubFmls();
            List<STLCost> ret = new ArrayList<>();
            ret.add(new STLAnd(subFmls.get(0), subFmls.get(1)));

            strengthenSTL(subFmls.get(0)).forEach(s -> ret.add(new STLOr(s, subFmls.get(1))));
            strengthenSTL(subFmls.get(1)).forEach(s -> ret.add(new STLOr(subFmls.get(0), s)));
            return ret;
        }
        if (stl instanceof STLAnd) {
            List<STLCost> subFmls = ((STLAnd) stl).getSubFmls();
            List<STLCost> ret = new ArrayList<>();

            strengthenSTL(subFmls.get(0)).forEach(s -> ret.add(new STLAnd(s, subFmls.get(1))));
            strengthenSTL(subFmls.get(1)).forEach(s -> ret.add(new STLAnd(subFmls.get(0), s)));
            return ret;
        }
        if (stl instanceof STLGlobal) {
            STLCost subFml = ((STLGlobal) stl).getSubFml();
            List<STLCost> ret = new ArrayList<>();
            strengthenSTL(subFml).forEach(s -> ret.add(new STLGlobal(s)));
            return ret;
        }
        if (stl instanceof STLUntil) {
            STLCost subFmlLeft = ((STLUntil) stl).getLeft();
            STLCost subFmlRight = ((STLUntil) stl).getRight();

            return new ArrayList<>(Arrays.asList(
                    new STLAnd(new STLGlobal(subFmlLeft), new STLGlobal(subFmlRight)),
                    new STLAnd(new STLGlobal(subFmlLeft), new STLEventually(new STLGlobal(subFmlRight))),
                    new STLAnd(new STLGlobal(subFmlLeft), new STLGlobal(new STLEventually(subFmlRight)))
            ));
        }
        if (stl instanceof STLEventually) {
            STLCost subFml = ((STLEventually) stl).getSubFml();
            return new ArrayList<>(Arrays.asList(
                    new STLGlobal(subFml),
                    new STLEventually(new STLGlobal(subFml)),
                    new STLGlobal(new STLEventually(subFml))
            ));
        }
        return Collections.emptyList();
    }

    private STLCost nextStrengthenedSTL(int targetIdx) {
        return this.candidateSTLproperties.get(targetIdx).remove(0);
    }

    private static class IntervalSTL {
        public STLSub stl;
        public Function<STLCost, STLCost> frame;
        private boolean isSTLEventually;
        private boolean isAssignedCurrent = false;
        private boolean isEventuallyInterval = false;
        private final int defaultFrom, defaultTo;
        private int currentFrom, currentTo;

        public IntervalSTL(STLSub stl, Function<STLCost, STLCost> frame) {
            this.stl = stl;
            this.defaultFrom = stl.getFrom();
            this.defaultTo = stl.getTo();
            this.frame = frame;

            STLCost subFml = this.stl.getSubFml();
            this.isSTLEventually = subFml instanceof STLEventually;
        }

        public STLCost getOriginalSTL() {
            return this.frame.apply(stl);
        }

        public STLCost strengthInit() {
            STLCost subFml = this.stl.getSubFml();
            if (subFml instanceof STLGlobal) {
                return this.frame.apply(subFml);
            } else if (subFml instanceof STLEventually) {
                STLCost subFml2 = ((STLEventually) subFml).getSubFml();
                return this.frame.apply(new STLGlobal(subFml2));
            }
            // TODO: implement Until
            return null;
        }

        /**
         * strengthen an STL formula by changing an interval
         * @return a strengthened STL formula
         */
        public STLCost nextStrengthedSTL() {
            if (!isAssignedCurrent) {
                isAssignedCurrent = true;
                this.currentFrom = 0;
                this.currentTo = 15;
                STLTemporalOp subFml = this.stl.getSubFml();
                if (subFml instanceof STLGlobal) {
                    this.currentFrom = this.defaultFrom * 3 / 4;
                    this.currentTo = this.defaultTo + ((30 - this.defaultTo) / 2);
                    return this.frame.apply(new STLSub(subFml, currentFrom, currentTo));
                } else if (subFml instanceof STLEventually) {
                    this.currentFrom = this.defaultFrom / 2;
                    this.currentTo = this.defaultFrom + ((30 - this.defaultFrom) / 2);
                    STLCost subFml2 = ((STLEventually) subFml).getSubFml();
                    return this.frame.apply(new STLSub(new STLGlobal(subFml2), currentFrom, currentTo));
                }
                return null;
            }
            if (isSTLEventually && isEventuallyInterval) {
                // when changing the interval of Eventually operator
                if (this.currentFrom > this.defaultFrom && (this.currentFrom - this.defaultFrom) / 2 > 0) {
                    // if 'from' number of the interval is able to change
                    this.currentFrom = this.defaultFrom + ((this.currentFrom - this.defaultFrom) / 2);
                } else {
                    // now change 'to' number of the interval
                    if (this.currentTo >= this.defaultTo || ((this.defaultTo - this.currentTo) / 2) == 0) {
                        // end
                        return null;
                    }
                    this.currentTo = this.defaultTo - ((this.defaultTo - this.currentTo) / 2);
                }
                STLTemporalOp subFml = this.stl.getSubFml();
                if (subFml instanceof STLEventually) {
                    return this.frame.apply(new STLSub(subFml, currentFrom, currentTo));
                }
                return null;
            } else {
                // when changing the interval of Globally operator
                if (this.currentFrom < this.defaultFrom && (this.defaultFrom - this.currentFrom) / 2 > 0) {
                    // if 'from' number of the interval is able to change
                    this.currentFrom = this.currentFrom + ((this.defaultFrom - this.currentFrom) / 2);
                } else {
                    // now change 'to' number of the interval
                    if (isSTLEventually) {
                        // if the most outer operator of target STL formula is Eventually
                        if (this.currentTo <= this.defaultFrom + 1) {
                            // change Eventually operator to Globally operator
                            this.isEventuallyInterval = true;
                            this.currentFrom = this.defaultFrom;
                            this.currentTo = this.defaultFrom;
                            STLEventually subFml = (STLEventually) this.stl.getSubFml();
                            return this.frame.apply(new STLSub(subFml, defaultFrom, defaultFrom));
                        }
                        this.currentTo = this.defaultFrom + ((this.currentTo - this.defaultFrom) / 2);
                    } else {
                        // if the most outer operator of target STL formula is Globally
                        if (this.currentTo <= this.defaultTo || ((this.currentTo - this.defaultTo) / 2) == 0) {
                            // end
                            return null;
                        }
                        // decrease to half
                        this.currentTo = this.defaultTo + ((this.currentTo - this.defaultTo) / 2);
                    }
                }

                STLTemporalOp subFml = this.stl.getSubFml();
                if (subFml instanceof STLGlobal) {
                    return this.frame.apply(new STLSub(subFml, currentFrom, currentTo));
                } else if (subFml instanceof STLEventually) {
                    STLCost subFml2 = ((STLEventually) subFml).getSubFml();
                    return this.frame.apply(new STLSub(new STLGlobal(subFml2), currentFrom, currentTo));
                }
                return null;
            }
        }
    }
}
