import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Command, Instance } from '@yamcs/client';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { IssueCommandDialog } from './IssueCommandDialog';

@Component({
  templateUrl: './CommandPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPage {

  instance: Instance;
  command$ = new BehaviorSubject<Command | null>(null);

  constructor(
    route: ActivatedRoute,
    private yamcs: YamcsService,
    private title: Title,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
  ) {
    this.instance = yamcs.getInstance();

    // When clicking links pointing to this same component, Angular will not reinstantiate
    // the component. Therefore subscribe to routeParams
    route.paramMap.subscribe(params => {
      const qualifiedName = params.get('qualifiedName')!;
      this.changeCommand(qualifiedName);
    });
  }

  changeCommand(qualifiedName: string) {
    this.yamcs.getInstanceClient()!.getCommand(qualifiedName).then(command => {
      this.command$.next(command);
      this.title.setTitle(command.name);
    });
  }

  issueCommand() {
    const dialogRef = this.dialog.open(IssueCommandDialog, {
      width: '400px',
      data: { command: this.command$.value! },
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        console.log('got result', result);
        this.snackBar.open(`Command issued`, undefined, {
          horizontalPosition: 'end',
          duration: 3000,
        });
      }
    });
  }
}
