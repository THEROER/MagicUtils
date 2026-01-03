import os


def define_env(env):
    version = os.getenv("MIKE_VERSION") or os.getenv("GITHUB_REF_NAME")
    if version and version.startswith("v"):
        version = version[1:]
    env.variables["magicutils_version"] = version or "dev"
