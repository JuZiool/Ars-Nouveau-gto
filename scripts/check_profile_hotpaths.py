"""Compare changed Java methods with the pre-optimization code, without a mod build.

Run with Python 3 and a JDK 21+ java executable:
    python scripts/check_profile_hotpaths.py --java <jdk>/bin/java

Only the selected method bodies run, in a temporary Java source-file harness with
minimal world/provider doubles. This does not validate Forge/Mixin integration.
"""

import argparse
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = "src/main/java/com/hollingsworth/arsnouveau/"
BASELINE = "4138e7da2"
METHODS = {
    "api/util/SourceUtil.java": [
        "public static @Nullable ISpecialSourceProvider takeSource(",
    ],
    "api/spell/SpellStats.java": [
        "public int getBuffCount(",
        "public boolean hasBuff(",
    ],
    "api/util/SpellUtil.java": [
        "private static Set<BlockPos> DFSBlockstates(",
    ],
}


def method(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end].replace("@Nullable ", "")


def methods(before):
    bodies = []
    for relative, signatures in METHODS.items():
        path = JAVA_ROOT + relative
        source = (
            subprocess.check_output(
                ["git", "show", f"{BASELINE}:{path}"], cwd=ROOT
            ).decode("utf-8")
            if before
            else (ROOT / path).read_text(encoding="utf-8")
        )
        bodies.extend(method(source, signature) for signature in signatures)
    return "\n".join(bodies)


HARNESS = r"""
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

class ProfileHotPathCheck {
    static class Before extends Hooks { BEFORE_METHODS }
    static class After extends Hooks { AFTER_METHODS }

    record AbstractAugment(int id) {}
    record BlockState(boolean match) {}
    record BlockPos(int x, int y, int z) {
        BlockPos offset(int dx, int dy, int dz) { return new BlockPos(x+dx, y+dy, z+dz); }
        BlockPos immutable() { return this; }
        static List<BlockPos> betweenClosed(BlockPos a, BlockPos b) {
            var result = new ArrayList<BlockPos>();
            for (int z=Math.min(a.z,b.z); z<=Math.max(a.z,b.z); z++)
                for (int y=Math.min(a.y,b.y); y<=Math.max(a.y,b.y); y++)
                    for (int x=Math.min(a.x,b.x); x<=Math.max(a.x,b.x); x++)
                        result.add(new BlockPos(x,y,z));
            return result;
        }
        static Stream<BlockPos> betweenClosedStream(BlockPos a, BlockPos b) {
            return betweenClosed(a,b).stream();
        }
    }
    static class Level {
        final Set<BlockPos> blocks = new HashSet<>();
        final List<BlockPos> reads = new ArrayList<>();
        BlockState getBlockState(BlockPos pos) {
            reads.add(pos);
            return new BlockState(blocks.contains(pos));
        }
    }
    static class ISpecialSourceProvider {
        final int id;
        int amount, removals;
        ISpecialSourceProvider(int id, int amount) { this.id=id; this.amount=amount; }
        SourceTile getSource() { return new SourceTile(this); }
        void removeSource(int cost) { amount-=cost; removals++; }
    }
    static class SourceTile {
        final ISpecialSourceProvider provider;
        SourceTile(ISpecialSourceProvider provider) { this.provider=provider; }
        int getSource() { return provider.amount; }
        void removeSource(int cost) { provider.removeSource(cost); }
    }
    static class SourceManager {
        static final SourceManager INSTANCE = new SourceManager();
        final ArrayList<ISpecialSourceProvider> jars = new ArrayList<>();
        final ArrayList<ISpecialSourceProvider> special = new ArrayList<>();
        final ArrayList<ISpecialSourceProvider> compatibility = new ArrayList<>();
        int compatibilityScans;
        ArrayList<ISpecialSourceProvider> findJars(BlockPos pos, Level level, int range,
                                                  boolean accepting, int minimum) {
            var result = new ArrayList<ISpecialSourceProvider>();
            if (range>=0) for (var jar:jars) if (jar.amount>=minimum) result.add(jar);
            return result;
        }
        ISpecialSourceProvider hasSourceNearby(BlockPos pos, Level level, int range, int minimum) {
            if (range>=0) for (var provider:special)
                if (provider.amount>=minimum) return provider;
            return null;
        }
        void reset(int[] amounts, int jarCount, int specialCount) {
            jars.clear(); special.clear(); compatibility.clear(); compatibilityScans=0;
            for (int i=0;i<amounts.length;i++)
                (i<jarCount ? jars:i<jarCount+specialCount ? special:compatibility)
                    .add(new ISpecialSourceProvider(i,amounts[i]));
        }
    }
    static class Hooks {
        List<AbstractAugment> augments = new ArrayList<>();
        static List<ISpecialSourceProvider> canTakeSource(BlockPos pos, Level level, int range) {
            var manager=SourceManager.INSTANCE;
            manager.compatibilityScans++;
            var result=manager.findJars(pos,level,range,false,1);
            if (range>=0) for (var provider:manager.special)
                if (provider.amount>0) result.add(provider);
            if (range>=0) for (var provider:manager.compatibility)
                if (provider.amount>0) result.add(provider);
            return result;
        }
    }
    static String snapshot(ISpecialSourceProvider chosen) {
        var manager=SourceManager.INSTANCE;
        var result=new StringBuilder().append(chosen==null ? -1:chosen.id);
        for (var group:List.of(manager.jars,manager.special,manager.compatibility))
            for (var provider:group) result.append(':').append(provider.amount)
                .append('/').append(provider.removals);
        return result.toString();
    }
    public static void main(String[] args) {
        var random=new Random(20261017);
        var manager=SourceManager.INSTANCE;
        var origin=new BlockPos(0,0,0);
        var level=new Level();
        for (int trial=0;trial<5000;trial++) {
            int[] amounts=random.ints(random.nextInt(12),0,101).toArray();
            int jars=random.nextInt(amounts.length+1), cost=random.nextInt(104)-2;
            int special=random.nextInt(amounts.length-jars+1);
            int range=trial%50==0 ? -1:10;
            manager.reset(amounts,jars,special);
            String before=snapshot(Before.takeSource(origin,level,range,cost));
            manager.reset(amounts,jars,special);
            String after=snapshot(After.takeSource(origin,level,range,cost));
            if (!before.equals(after)) throw new AssertionError("provider/order/deduction mismatch");
            boolean indexSuffices=false;
            for (int i=0;i<jars+special;i++)
                indexSuffices |= range>=0 && amounts[i]>=Math.max(1,cost);
            if (manager.compatibilityScans!=(indexSuffices ? 0:1))
                throw new AssertionError("jar fast path or compatibility fallback missed");
        }
        var beforeStats=new Before(); var afterStats=new After();
        for (int trial=0;trial<1000;trial++) {
            var augments=new ArrayList<AbstractAugment>();
            for (int i=random.nextInt(40);i>0;i--)
                augments.add(random.nextBoolean() ? null:new AbstractAugment(random.nextInt(8)));
            beforeStats.augments=augments; afterStats.augments=augments;
            for (int id=0;id<9;id++) {
                var query=new AbstractAugment(id);
                if (beforeStats.getBuffCount(query)!=afterStats.getBuffCount(query)
                    || beforeStats.hasBuff(query)!=afterStats.hasBuff(query))
                    throw new AssertionError("augment equality/count mismatch");
            }
        }
        for (int trial=0;trial<300;trial++) {
            level.blocks.clear(); level.reads.clear();
            for (int x=-3;x<=3;x++) for (int y=-3;y<=3;y++) for (int z=-3;z<=3;z++)
                if (random.nextInt(3)==0) level.blocks.add(new BlockPos(x,y,z));
            if (trial%2==0) level.blocks.add(origin);
            int limit=random.nextInt(160)-1;
            var before=Before.DFSBlockstates(level,List.of(origin),limit,BlockState::match);
            var reads=new ArrayList<>(level.reads); level.reads.clear();
            var after=After.DFSBlockstates(level,List.of(origin),limit,BlockState::match);
            if (!before.equals(after) || !reads.equals(level.reads))
                throw new AssertionError("tree traversal/order/limit mismatch");
        }
        System.out.println("PASS: 5000 source lookups, 9000 augment queries, 300 tree traversals");
    }
}
"""


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    source = HARNESS.replace("BEFORE_METHODS", methods(True)).replace(
        "AFTER_METHODS", methods(False)
    )
    with tempfile.TemporaryDirectory(prefix="ars-hotpaths-") as directory:
        path = Path(directory) / "ProfileHotPathCheck.java"
        path.write_text(source, encoding="utf-8")
        subprocess.run([args.java, "--source", "21", str(path)], check=True, cwd=ROOT)
