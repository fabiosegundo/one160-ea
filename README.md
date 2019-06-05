# one160-ea
This is the Opportunistic Network Environment (ONE) with energy awareness improvements

Under Licence GPLv3

The original source code is in https://akeranen.github.io/the-one/

Improvements in the energy model:
- Energy for reception, base interface energy, sleep energy

Improvements in the modules:
- Work with multiple network interfaces
- Added sleep and wakeup functions and a Sync-IS mechanism (Synchronized Intermittent Sleeping)

New options in configuration file:
For interface
- sleepEnergy : energy spent in sleep mode (for group too)
- ibaseEnergy : energy spent in base operation of the interface (for group too)
- receiveEnergy: energy spent in reception (for group too)
- disconnectWhenNotActive : true*/false - if destroy connection on interface when not become inactive (by sleep, activemovement(power off), or no energy)
- syncIS=vl1,vl2: ex: 5,10: synchronized intermittent sleep of 5 awake of 10 seconds (5 sleeping)

Other improvements:
- external movement file can receive directly GPS coordinates;
- energy level report also sent partial results of energy consumption;

To do:
- another way to fast detection of the interface type;
- a faster way to verify the power consumption in reception;
- clear the source code and improve documentation;

More documentation in the source code.


