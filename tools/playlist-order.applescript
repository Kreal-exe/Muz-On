tell application "Music"
 set out to {}
 repeat with p in (every user playlist whose special kind is none)
  set end of out to "#" & (persistent ID of p)
  set out to out & (persistent ID of every track of p)
 end repeat
end tell
set AppleScript's text item delimiters to linefeed
return out as text
