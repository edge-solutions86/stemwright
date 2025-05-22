import { spawnSync } from 'node:child_process';
import { argv, exit, stdout } from 'node:process';

/**
 * Everything that has to pass before a change is worth pushing, behind one
 * command, in the order that fails fastest.
 *
 * Each stage is named, and a single stage can be run on its own while a change
 * is being worked on. The order is not decorative: formatting and linting are
 * seconds and catch the most, the type check is next, and the suite is last
 * because it is the slowest and the least likely to be the thing that is wrong.
 */
export const STAGES = [
  { name: 'format', command: 'npx', args: ['prettier', '--check', '.'] },
  { name: 'lint', command: 'npx', args: ['eslint', '.'] },
  { name: 'types', command: 'npx', args: ['tsc', '--noEmit'] },
  { name: 'build', command: 'npx', args: ['tsc', '-p', 'tsconfig.build.json', '--noEmit'] },
  { name: 'test', command: 'npx', args: ['vitest', 'run'] },
];

export function stageNamed(name) {
  return STAGES.find((stage) => stage.name === name);
}

export function planFor(wanted) {
  if (wanted.length === 0) {
    return STAGES;
  }
  return wanted.map((name) => {
    const stage = stageNamed(name);
    if (stage === undefined) {
      throw new Error(
        `there is no stage named ${name}, try one of ${STAGES.map((s) => s.name).join(', ')}`,
      );
    }
    return stage;
  });
}

export function runStage(stage) {
  const started = Date.now();
  const result = spawnSync(stage.command, stage.args, { stdio: 'inherit', shell: true });
  return {
    name: stage.name,
    ok: result.status === 0,
    ms: Date.now() - started,
  };
}

export function main(wanted) {
  const plan = planFor(wanted);
  const results = [];

  for (const stage of plan) {
    stdout.write(`\n--- ${stage.name} ---\n`);
    const result = runStage(stage);
    results.push(result);
    if (!result.ok) {
      break;
    }
  }

  stdout.write('\n');
  for (const result of results) {
    stdout.write(`${result.ok ? 'ok  ' : 'FAIL'} ${result.name} ${result.ms}ms\n`);
  }

  const failed = results.filter((result) => !result.ok);
  const skipped = plan.length - results.length;
  if (skipped > 0) {
    stdout.write(`${skipped} stages were not reached\n`);
  }
  return failed.length === 0 ? 0 : 1;
}

if (argv[1]?.endsWith('verify.mjs') === true) {
  exit(main(argv.slice(2)));
}
