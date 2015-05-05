def branches = ['master', 'collectd-5.4']
branches.each {
  def branchName = "${it}"

  job("make-dist-tarball-${branchName}") {
    displayName("prepare tarball for deb/rpm packages (${branchName} branch)")
    description("""
This job generates a release tarball out of the '${branchName}' branch and archives it for downstream consumption.

Configuration generated automatically, do not edit!
""")
    blockOnDownstreamProjects()

    scm {
      git {
        remote {
          name('origin')
          url('https://github.com/collectd/collectd.git')
        }
        branch("origin/${branchName}")
      }
    }

    triggers {
      scm('@hourly')
    }

    steps {
      shell('/usr/local/bin/make-dist-archive.sh $GIT_COMMIT')
    }

    publishers {
      archiveArtifacts {
        pattern('collectd*.tar.bz2')
        pattern('env.sh')
        pattern('collectd.spec')
      }
      downstream("make-deb-pkgs-${branchName}", 'SUCCESS')
      downstream("make-rpm-pkgs-${branchName}", 'SUCCESS')
    }
  }

  matrixJob("make-deb-pkgs-${branchName}") {
    displayName("build deb packages for Debian/Ubuntu LTS (${branchName} branch)")
    description("""
This job:
 * extracts the tarball passed down from the 'make-dist-tarball-${branchName}' job
 * builds .deb packages for various distros
 * pushes the result to the repository hosted at http://ci.collectd.org/

Configuration generated automatically, do not edit!
""")
    runSequentially(true)

    axes {
      text('distro', 'precise', 'trusty', 'squeeze', 'wheezy', 'jessie')
      text('arch', 'i386', 'amd64')
    }

    steps {
      copyArtifacts("make-dist-tarball-${branchName}", 'collectd*.tar.bz2, env.sh') {
        upstreamBuild(true)
      }

      shell('/usr/local/bin/make-debs.sh $distro $arch')
      shell('/usr/local/bin/s3-apt-repo.sh')
    }
  }

  matrixJob("make-rpm-pkgs-${branchName}") {
    displayName("build rpm packages for CentOS/EPEL (${branchName} branch)")
    description("""
This job:
 * extracts the tarball passed down from the 'make-dist-tarball-${branchName}' job
 * builds .rpm packages for various distros
 * pushes the result to the repository hosted at http://ci.collectd.org/

Configuration generated automatically, do not edit!
""")
    runSequentially(true)

    axes {
      text('distro', 'epel-5-i386', 'epel-5-x86_64', 'epel-6-i386', 'epel-6-x86_64', 'epel-7-x86_64')
    }

    steps {
      copyArtifacts("make-dist-tarball-${branchName}", 'collectd*.tar.bz2, env.sh, collectd.spec') {
        upstreamBuild(true)
      }

      shell('/usr/local/bin/make-rpms.sh $distro')
      shell('/usr/local/bin/s3-yum-repo.sh')
    }
  }
}