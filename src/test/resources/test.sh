#!/usr/bin/env bash

DIR="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

set -euo pipefail

ID=$(uuid)

clean() {
    if [[ -d /tmp/rep_1 ]];then
        echo "Cleaning down the repo"
        rm -rf /tmp/rep_1
    fi
}


test_good() {
    echo "Cloning the repository to local"

    cd /tmp/
    git clone http://admin:admin@localhost:7990/bitbucket/scm/project_1/rep_1.git
    cd rep_1
    cp ${DIR}/good.yaml ./good-${ID}.yaml
    cp ${DIR}/multi-good.yaml ./multi-good-${ID}.yaml
    git add .
    git commit -am "Testing with good ${ID}"
    git push origin master

}

test_bad() {

    # The following should fail so lets make sure that it does
    set +e
    cp ${DIR}/bad.yaml ./bad-${ID}.yaml
    git add .
    git commit -am "Testing with single bad ${ID}"
    git push origin master
    if [[ ! $? ]];then
        echo "ERROR: If the previous command worked then the hook didn't work - bad ${ID}"
        exit 1
    fi

    cp ${DIR}/multi-bad.yaml ./multi-bad-${ID}.yaml
    git add .
    git commit -am "Testing with multi bad ${ID}"
    git push origin master
    if [[ ! $? ]];then
        echo "ERROR: If the previous command worked then the hook didn't work - multi bad ${ID}"
        exit 1
    fi

}

enable_plugin() {
    curl -u admin:admin -X PUT -H "Content-Type: application/json" \
        http://localhost:7990/bitbucket/rest/api/1.0/projects/PROJECT_1/repos/rep_1/settings/hooks/com.mcmanus.scm.stash.yaml-validator-hook:yaml-validator-hook/enabled \
        -d '{"extension":"yaml"}'

}

pushd .
clean
enable_plugin
test_good
test_bad
clean
popd