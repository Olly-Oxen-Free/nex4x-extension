# Build & Deployment Commands

## Compilation
```bash
cd /home/jayden-eppcohen/Documents/Projects/Starsector-Mods/workspace/nexerelin-4x-mod
./build.sh
```
Expected output: "BUILD SUCCESSFUL" and JAR file created at `jars/Nex4xExpansion.jar`

## Git Operations
```bash
# View status
git status

# Add files
git add <paths>

# Commit with coauthor
git commit -m "message"  # Will add Co-Authored-By: Claude Haiku 4.5 <noreply@anthropic.com>

# Check log
git log --oneline -10
```

## In-Game Testing (via debug console)
```
runcode nex4x.debug.Nex4xDebugCommand.printLeaders();
runcode nex4x.debug.Nex4xDebugCommand.openViceroy("hegemony");
runcode nex4x.debug.Nex4xDebugCommand.openLeader("hegemony");
```

## Searching & Inspection
```bash
# Find files
find . -name "*.java" | grep pattern

# Search code patterns
grep -r "class LeaderRegistry" src/

# List source directory structure
ls -la src/nex4x/leaders/
```

## Key Paths
- Source: `/home/jayden-eppcohen/Documents/Projects/Starsector-Mods/workspace/nexerelin-4x-mod/src/nex4x/`
- Build output: `.../jars/Nex4xExpansion.jar`
- Config: `./data/config/nex4x/`
