# Questions and Clarifications (Q&A)

Based on the requirements in `req.txt`, here are a few clarification questions to ensure the implementation perfectly matches your expectations:

1. **Handling Multiple System Settings Panels**
Android generally won't allow automatically opening multiple Settings panels at exactly the same time securely without them overlapping. If a user checks multiple boxes (e.g., "Open WiFi Settings", "Open Airplane Mode Settings", and "Open Internet Panel"), do you want the app to open them sequentially as they return from each panel or just trigger them sequentially instantly (user might have to back out of each manually to see the next)?

2. **App "Hides Itself" Behavior**
You mentioned "the app hides itself (still in recent)". Does this mean the app should programmatically go to the background (simulating a Home button press) automatically after the user initiates the snooze and the relevant panels are checked?

3. **Mute/Volume Implementation**
"mute media" and "mute ring + notification" are mentioned. Should the app just set the standard `AudioManager` volumes (Stream Music, Stream Ring, Stream Notification) to 0 and restore them later? Or would you also like the app to request "Do Not Disturb" (Notification Policy Access) permission to ensure complete silencing?

4. **Reboot Survival Behavior**
When the phone reboots, a `BOOT_COMPLETED` receiver will run.
* Scenario A: The original snooze time *has not expired*. The app will re-schedule the remaining time. 
* Scenario B: The device was off and the original snooze time *has already expired* by the time it reboots. Should the app immediately restore the volumes upon boot?

5. **Cancel Snooze Button**
You specified "when snoozing on top a small cancel snooze button with just the cancel-snooze.png icon under docs/images/". Should this icon be placed simply at the top of the `MainActivity` layout (meaning the user opens the app to cancel)? Or does it need to be a "floating" icon over everything (which requires the "Draw over other apps" permission)?

6. **Missing Hour Picker Limit**
The example code sets `hourPicker.maxValue = 15`, but the requirements mention `0 to 23 hours`. I will assume it should be 0 to 23. Are there any restrictions on the combined total time (e.g., must be at least 1 minute)?
