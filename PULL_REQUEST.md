# Pull Request

## Proposed changes

Provide high level description of what the changes are.

## Checklist

- Existing tests are passing.
- New tests are included if new functionalities are added.
- Documentation is updated or added.


## Steps

## Preparation

#### Fork and Clone

Fork the project [on GitHub](https://github.com/kenrui-group/packetbroker) and clone the fork locally.

```text
$ git clone https://github.com/username/packetbroker.git
$ cd packetbroker
```

#### Add Upstream

Add the upstream so any changes can be pulled in to your clone.

```text
$ git remote add upstream https://github.com/kenrui-group/packetbroker.git
```

## Code

#### Branch

Branch from master to do the development.

```text
$ git checkout -b my-branch
```

#### Fetch Upstream Changes

Fetch new changes from upstream from time to time as you code.

```text
# Fetch from upstream remote
$ git fetch upstream

# View all branches, including those from upstream
$ git branch -va

# Checkout your local master branch and merge in changes from upstream/master 
$ git checkout master
$ git merge upstream/master

# Rebase on latest master
$ git rebase master
```

## Submit PR

Before doing anything ensure your local repo has the latest from upstream by doing [Fetch Upstream Changes](#fetch-upstream-changes).

### Squash and Rebase

Clean up your branch by doing a rebase on the latest upstream master to make sure there are no integration issues. 
It is a good idea to squash all local commits on the feature or topic branch to keep the history clean.

```text
$ git checkout my-branch
$ git rebase -i master
```

### Push

Push the feature or topic branch to your fork.

```text
$ git push origin my-branch
``` 

### Raise PR

From within GitHub, open a PR from your branch in the fork to the upstream.

### Discuss and Update

If additional changes are required, update your branch.  Remember to [Fetch Upstream Changes](#fetch-upstream-changes) if upstream has changed.

```text
$ git add updated/changed/files
$ git commit
$ git push origin my-branch
```