#!/usr/bin/env python3
"""Check archive or fixture contents without storing private identifiers in Git.

The owner policy is a mode-0600 JSON file in the user’s Slabbed configuration directory.
It contains given_name, family_name and account_name. SLABBED_PRIVACY_POLICY
can select a different owner-only file. A successful owner scan prints the
content fingerprint for tools/reviewed-content.json. The review key signs the
policy and the reviewed fingerprints via ssh-keygen -Y sign, using the namespaces
slabbed-privacy-policy and slabbed-reviewed-content respectively. Each signature
lives beside its input with the .sig suffix. A checkout without the policy
accepts only signed review records.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import stat
import subprocess
import sys
import zipfile


class PrivacyError(ValueError):
    """A check failure whose message is safe to display."""


def signed_json(path, namespace):
    data = path.read_bytes()
    reviewers = Path(__file__).with_name("discretion-reviewers")
    result = subprocess.run(
        ["ssh-keygen", "-Y", "verify", "-f", str(reviewers), "-I", "maintainer",
         "-n", namespace, "-s", str(path) + ".sig"],
        input=data, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    if result.returncode:
        raise PrivacyError("Owner verification signature is missing or invalid.")
    return json.loads(data)


def private_tokens():
    configured = os.environ.get("SLABBED_PRIVACY_POLICY")
    path = Path(configured) if configured is not None else Path.home() / ".config/slabbed/privacy-policy.json"
    try:
        info = path.lstat()
    except FileNotFoundError:
        if configured is not None:
            raise PrivacyError("Configured privacy policy is missing.")
        return None
    if not stat.S_ISREG(info.st_mode) or (
        os.name == "posix" and (info.st_uid != os.getuid() or info.st_mode & 0o077)
    ):
        raise PrivacyError("Privacy policy must be a regular, owner-only file.")
    fields = signed_json(path, "slabbed-privacy-policy")
    if not isinstance(fields, dict) or not {"given_name", "family_name", "account_name"} <= fields.keys():
        raise PrivacyError("Privacy policy requires given_name, family_name and account_name.")
    if any(not isinstance(v, str) or not v.strip() for v in fields.values()):
        raise PrivacyError("Privacy policy contains an invalid literal.")
    return {v.encode("utf-8").lower() for v in fields.values()}


def entries(label, path):
    if label == "fixture":
        for member in sorted(path.rglob("*")):
            if member.is_symlink():
                raise PrivacyError("Fixture contains a symbolic link.")
            if member.is_file():
                yield member.relative_to(path).as_posix(), member.read_bytes()
    else:
        with zipfile.ZipFile(path) as archive:
            for member in sorted(archive.infolist(), key=lambda item: item.filename):
                if not member.is_dir():
                    yield member.filename, archive.read(member)


# These two variable manifest fields have a restricted alphabet and format.
# Every other byte, including malformed values and continuation lines, stays bound.
BUILD_TIME = re.compile(rb"(?m)^(Slabbed-Build-Time: )\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z(?=\r?$)")
GIT_SHA = re.compile(rb"(?m)^(Slabbed-Git-Sha: )(?:[a-f0-9]{7,64}(?:-dirty)?|unknown)(?=\r?$)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("label", choices=("binary", "sources", "fixture"))
    parser.add_argument("path", type=Path)
    args = parser.parse_args()
    tokens = private_tokens()
    digest = hashlib.sha256()
    count = 0
    for name, data in entries(args.label, args.path):
        count += 1
        encoded = name.encode("utf-8")
        if tokens is not None:
            content = data.lower()
            entry_name = encoded.lower()
            if any(word in entry_name or word in content for word in tokens):
                raise PrivacyError("Private identifier found in " + args.label + " contents.")
        # Bind every entry name and payload; ZIP timestamps and compression are immaterial.
        if args.label != "fixture" and name == "META-INF/MANIFEST.MF":
            data = BUILD_TIME.sub(rb"\g<1>2000-01-01T00:00:00Z", data)
            data = GIT_SHA.sub(rb"\g<1>000000000000", data)
        digest.update(encoded + b"\0" + hashlib.sha256(data).digest())
    if not count:
        raise PrivacyError("No entries found to verify.")
    fingerprint = digest.hexdigest()
    if tokens is None:
        reviewed = signed_json(Path(__file__).with_name("reviewed-content.json"), "slabbed-reviewed-content")
        if fingerprint != reviewed[args.label]:
            raise PrivacyError("Changed " + args.label + " contents require owner privacy verification.")
    print(f"Privacy verified ({args.label}): {fingerprint}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, KeyError, ValueError, zipfile.BadZipFile) as error:
        # Paths and policy text may contain identifying information.
        message = str(error) if isinstance(error, PrivacyError) else "Privacy inputs could not be read."
        sys.exit(message)
