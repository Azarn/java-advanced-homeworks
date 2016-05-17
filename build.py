#!/usr/bin/env python3

import sys
import os.path
import shutil
import subprocess
import difflib
import re


class JavaBuilderException(Exception):
    ...


class JavaBuilder:
    SRC_DIR = 'src'
    OUT_DIR = 'out'
    MANIFEST_DIR = 'META-INF'
    MANIFEST_NAME = 'MANIFEST.MF'
    MANIFEST_FORMAT = 'Manifest-Version: 1.0\nClass-Path: {1}.jar\nMain-Class: {0}.{2}.{3}\n'
    MAIN_SIGNATURE = r'\s*public\s+static\s+void\s+main\s*\(\s*String\s*\[\]'
    TESTER_CLASS = "Tester"
    JAVA_ADVANCED_PATH = '/root/code/java_advanced/java-advanced-2016'
    PATH_START = 'ru'
    PATH_PREFIX = os.path.join(PATH_START, 'ifmo', 'ctddev', 'kichigin')
    PACKAGE_PREFIX = PATH_PREFIX.replace(os.path.sep, '.')
    PACKAGE_PREFIX_TESTER = 'info.kgeorgiy.java.advanced'

    def __init__(self):
        if not os.path.exists(self.SRC_DIR):
            raise JavaBuilderException('Source dir {} does not exists!'.format(SRC_DIR))

        path = os.path.join(self.SRC_DIR, self.PATH_PREFIX)
        package_names = os.listdir(path)
        if len(package_names) != 1:
            raise JavaBuilderException('Package name predicting failed, too many variants')

        self.package_name = package_names[0]
        print('Predicted package name: "{}"'.format(self.package_name))

        path = os.path.join(path, self.package_name)
        self.package_path = path

        artifacts_path = os.path.join(self.JAVA_ADVANCED_PATH, 'artifacts')
        jars = [j[:-4] for j in os.listdir(artifacts_path) if j.endswith('.jar')]
        if not jars:
            raise JavaBuilderException('No artifacts found')

        gen = ((j, difflib.SequenceMatcher(None, j.lower(), self.package_name).ratio()) for j in jars)
        best_artifact = max(gen, key=lambda x: x[1])

        self.artifact = best_artifact[0]
        print('Predicted tester jar file: "{}.jar" (ratio test: {})'.format(*best_artifact))

        artifacts_src = os.path.join(self.JAVA_ADVANCED_PATH, 'java',
                                     self.PACKAGE_PREFIX_TESTER.replace('.', os.path.sep))

        # TODO: check source of best_artifact to match best_artifact_package
        gen = ((j, difflib.SequenceMatcher(None, j, self.package_name).ratio()) for j in os.listdir(artifacts_src))
        best_artifact_package = max(gen, key=lambda x: x[1])
        self.artifact_package = best_artifact_package[0]

        self.artifacts = '{0}/artifacts/{1}.jar:{0}/lib/*:{2}'.format(self.JAVA_ADVANCED_PATH,
                                                                      self.artifact, self.OUT_DIR)

        self.compile_java = [f for f in os.listdir(self.package_path) if f.endswith('.java')]
        if not self.compile_java:
            raise JavaBuilderException('Have not found any java source file!')
        print('Found java source:', ', '.join(map(lambda x: '"{}"'.format(x), self.compile_java)))

        self.runnable_java = []
        for j in self.compile_java:
            with open(os.path.join(self.package_path, j), 'r') as f:
                if re.search(self.MAIN_SIGNATURE, f.read()):
                    self.runnable_java.append(j)

        if not self.runnable_java:
            print('Runnable java sources not found')
        else:
            print('Found runnable java source:', ', '.join(map(lambda s: '"{}"'.format(s), self.runnable_java)))
        print()

    @staticmethod
    def _check_dir(directory, msg):
        if not (os.path.exists(directory)):
            os.mkdir(directory)
            print('{}: "{}"'.format(msg, directory))
            return False
        return True

    def _get_runner_class(self, *args):
        sel = 0
        if len(self.runnable_java) > 1:
            try:
                default_runnable = int(args[-1])
                if default_runnable < len(self.runnable_java):
                    sel = default_runnable
            except (IndexError, ValueError):
                ...
            print('Selected runnable is "{}" ({}), number is taken from the last argument or 0'.format(
                self.runnable_java[sel], sel))
        return self.runnable_java[sel][:-5]

    def run(self, cmd, *args):
        return self.COMMANDS[cmd](self, *args)

    @classmethod
    def sorted_commands(cls):
        return sorted(cls.COMMANDS.keys())

    def compile(self, *args):
        self._check_dir(self.OUT_DIR, 'Created output directory')

        j_args = ['javac', '-cp', self.artifacts, '-d', self.OUT_DIR,
                *(os.path.join(self.package_path, s) for s in self.compile_java)]

        print('\nCompiling:')
        print(' '.join(j_args))
        res = subprocess.run(j_args, stdin=sys.stdin, stdout=sys.stdout)
        return res.returncode == 0

    def manifest(self, *args):
        self._check_dir(self.MANIFEST_DIR, 'Created manifest directory')
        with open(os.path.join(self.MANIFEST_DIR, self.MANIFEST_NAME), 'wb') as f:
            data = self.MANIFEST_FORMAT.format(self.PACKAGE_PREFIX, self.artifact,
                                               self.package_name, self._get_runner_class(*args),
                                               self.PACKAGE_PREFIX_TESTER).encode('UTF-8')
            f.write(data)
        return True


    def make_jar(self, *args):
        # jar -cvfm $OURJAR META-INF/MANIFEST.MF  -C $BUILDDIR ./
        manifest_path = os.path.join(self.MANIFEST_DIR, self.MANIFEST_NAME)
        if not os.path.exists(manifest_path):
            print('Manifest does not exists, creating...')
            if not self.manifest(*args):
                raise JavaBuilderException('Error while creating manifest')
            print('OK\n')

        if not self._check_dir(self.OUT_DIR, 'Created output directory'):
            print('Now we have to compile...')
            if not self.compile(*args):
                raise JavaBuilderException('Error while building')
            print('OK\n')


        j_args = ['jar', '-cvfm', os.path.join(self.OUT_DIR, '{}.jar'.format(self.package_name)),
                manifest_path, '-C', self.OUT_DIR, self.PATH_START]
        res = subprocess.run(j_args, stdin=sys.stdin, stdout=sys.stdout)
        return res.returncode == 0

    def run_jar(self, *args):
        if not os.path.exists(os.path.join(self.OUT_DIR, '{}.jar'.format(self.package_name))):
            print('Jar file does not exists, creating...')
            if not self.make_jar(*args):
                raise('Jar creating error')
            print('OK\n')

        j_args = ['java', '-jar', os.path.join(self.OUT_DIR, '{}.jar'.format(self.package_name)),
                '-cp', self.artifacts]
        res = subprocess.run(j_args, stdin=sys.stdin, stdout=sys.stdout)
        return res.returncode == 0

    def run_class(self, *args):
        if (len(args) == 0):
            raise JavaBuilderException('Need at least one more argument for using `run_class`')

        if not [d for d in os.listdir(os.path.join(self.OUT_DIR, self.PATH_PREFIX,
                                                   self.package_name)) if d.endswith('.class')]:
            print('Have not found any class file, compiling...')
            if not self.compile(*args):
                raise JavaBuilderException('Error while building')
            print('OK\n')

        j_args = ['java', '-cp', self.artifacts,
                  '{}.{}.{}'.format(self.PACKAGE_PREFIX_TESTER, self.artifact_package, self.TESTER_CLASS), args[0],
                  '{}.{}.{}'.format(self.PACKAGE_PREFIX, self.package_name, self._get_runner_class(*args))]
        if len(args) > 2:
            j_args.append(args[1])

        res = subprocess.run(j_args, stdin=sys.stdin, stdout=sys.stdout)
        return res.returncode == 0

    def clean(self, *args):
        shutil.rmtree(self.OUT_DIR)
        shutil.rmtree(self.MANIFEST_DIR)
        return True

    #


    COMMANDS = {
        'compile': compile,
        'clean': clean,
        'manifest': manifest,
        'jar': make_jar,
        'run-jar': run_jar,
        'run-class': run_class,
    }

def main():
    if len(sys.argv) <= 1 or sys.argv[1] not in JavaBuilder.sorted_commands():
        print('Usage:', sys.argv[0], ' | '.join(JavaBuilder.sorted_commands()))
        return

    JavaBuilder().run(*sys.argv[1:])


if __name__ == '__main__':
    main()
