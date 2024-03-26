// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2019-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.convertwithmoss.format.exs;

/**
 * Structure for a EXS24 group.
 *
 * @author Jürgen Moßgraber
 */
class EXS24Group
{
    String  name;
    int     volume                    = 0;
    int     pan                       = 0;
    int     polyphony                 = 0;    // = Max
    int     options                   = 1;
    int     exclusive                 = 0;
    int     minVelocity               = 0;
    int     maxVelocity               = 127;
    int     sampleSelectRandomOffset  = 0;
    int     releaseTriggerTime        = 0;
    int     velocityRangExFade        = 0;
    int     velocityRangExFadeType    = 0;
    int     keyrangExFadeType         = 0;
    int     keyrangExFade             = 0;
    int     enableByTempoLow          = 80;
    int     enableByTempoHigh         = 140;
    int     cutoffOffset              = 0;
    int     resoOffset                = 0;
    int     env1AttackOffset          = 0;
    int     env1DecayOffset           = 0;
    int     env1SustainOffset         = 0;
    int     env1ReleaseOffset         = 0;
    boolean releaseTrigger            = false;
    int     output                    = 0;
    int     enableByNoteValue         = 0;
    int     roundRobinGroupPos        = -1;
    int     enableByType              = 0;
    int     enableByControlValue      = 0;
    int     enableByControlLow        = 0;
    int     enableByControlHigh       = 0;
    int     startNote                 = 0;
    int     endNote                   = 127;
    int     enableByMidiChannel       = 0;
    int     enableByArticulationValue = 0;
    int     enableByBenderLow         = 0;
    int     enableByBenderHigh        = 0;
    int     env1HoldOffset            = 0;
    int     env2AttackOffset          = 0;
    int     env2DecayOffset           = 0;
    int     env2SustainOffset         = 0;
    int     env2ReleaseOffset         = 0;
    int     env2HoldOffset            = 0;
    int     env1DelayOffset           = 0;
    int     env2DelayOffset           = 0;

    boolean mute                      = false;
    boolean releaseTriggerDecay       = false;
    boolean fixedSampleSelect         = false;

    boolean enableByNote              = false;
    boolean enableByRoundRobin        = false;
    boolean enableByControl           = false;
    boolean enableByBend              = false;
    boolean enableByChannel           = false;
    boolean enableByArticulation      = false;
    boolean enablebyTempo             = false;
}
