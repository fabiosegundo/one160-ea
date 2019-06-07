# one160-ea
This is a path to the Opportunistic Network Environment (ONE) 1.6.0 adding energy awareness improvements and other modifications.

The original source code is in https://akeranen.github.io/the-one/.
Under GPLv3 License.

Improvements in the energy model:
- Energy for reception, base interface energy, sleep energy.

Improvements in the modules:
- Work with multiple network interfaces;

New options in configuration file:
For interface
- sleepEnergy : energy spent in sleep mode (for group too);
- ibaseEnergy : energy spent in base operation of the interface (for group too);
- receiveEnergy: energy spent in reception (for group too);
- disconnectWhenNotActive : true*/false - if destroy connection on interface when not become inactive (by sleep, activemovement(power off), or no energy);
- syncIS=vl1,vl2: ex: 5,10: synchronized intermittent sleep of 5 awake of 10 seconds (5 sleeping).

Other improvements:
- external movement file can receive directly GPS coordinates;
- new queues stragety and queue strategy options for dropping; 
- energy level report also sent partial results of energy consumption.
- new network interfaces

To do:
- another way to fast detection of the interface type;
- a faster way to verify the power consumption in reception;
- clear the source code and improve documentation.

More documentation in the source code.


